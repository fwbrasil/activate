package net.fwbrasil.activate.migration

import language.postfixOps
import language.existentials
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.entity.Entity
import java.util.Date
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityMetadata
import java.lang.reflect.Modifier
import net.fwbrasil.activate.util.GraphUtil.DependencyTree
import net.fwbrasil.activate.util.GraphUtil.CyclicReferenceException
import scala.annotation.implicitNotFound
import net.fwbrasil.activate.entity.LazyList
import net.fwbrasil.activate.entity.EntityPropertyMetadata
import net.fwbrasil.activate.entity.id.UUID

class StorageVersion(
    val contextName: String,
    var lastScript: Long,
    var lastAction: Int)
        extends Entity
        with UUID

object Migration {

    private[activate] val storageVersionCache = MutableMap[String, StorageVersion]()

    private[activate] def storageVersion(ctx: ActivateContext) = {
        class StorageVersionMigration extends ManualMigration()(ctx) {
            override val name = "Initial database setup (StorageVersion)"
            override val developers = List("fwbrasil")
            def up = {
                createTableForEntity[StorageVersion]
                    .ifNotExists
                createInexistentColumnsForEntity[StorageVersion]
                table[StorageVersion].addIndex(
                    columnName = "contextName",
                    indexName = "IDX_CTX_NAME")
                    .ifNotExists
                customScript {
                    ctx.storages.foreach(_.prepareDatabase)
                }
            }
        }
        import ctx._
        storageVersionCache.getOrElseUpdate(context.name, {
            this.execute(context, new StorageVersionMigration)
            transactional {
                select[StorageVersion].where(_.contextName :== context.name)
                    .headOption
                    .getOrElse(new StorageVersion(context.name, -1, -1))
            }
        })
    }

    private def storageVersionTuple(ctx: ActivateContext) = {
        val version = storageVersion(ctx)
        ctx.transactional {
            (version.lastScript, version.lastAction)
        }
    }

    val migrationsCache = MutableMap[ActivateContext, List[Migration]]()

    def execute(context: ActivateContext, manualMigration: ManualMigration): Unit = {
        val setupActions =
            manualMigration.upActions
        setupActions.foreach(execute(context, _))
    }

    def revert(context: ActivateContext, manualMigration: ManualMigration): Unit = {
        val setupActions =
            manualMigration.downActions
        setupActions.foreach(execute(context, _))
    }

    def update(context: ActivateContext): Unit =
        updateTo(context, Long.MaxValue)

    def updateTo(context: ActivateContext, timestamp: Long): Unit =
        context.synchronized {
            execute(context, actionsOnInterval(context, storageVersionTuple(context), (timestamp, Int.MaxValue), false), false)
        }

    def revertTo(context: ActivateContext, timestamp: Long): Unit =
        context.synchronized {
            execute(context, actionsOnInterval(context, (timestamp, Int.MaxValue), storageVersionTuple(context), true), true)
        }

    private def migrations(context: ActivateContext) =
        migrationsCache.getOrElseUpdate(context, {
            val result =
                Reflection.getAllImplementorsNames(List(classOf[Migration], context.getClass), classOf[Migration])
                    .map(name => ActivateContext.classLoaderFor(name).loadClass(name))
                    .filter(e => !e.isInterface && !Modifier.isAbstract(e.getModifiers()) && !classOf[ManualMigration].isAssignableFrom(e))
                    .map(_.newInstance.asInstanceOf[Migration])
                    .toList
                    .sortBy(_.timestamp)
            val filtered =
                result.filter(_.context == context)
            verifyDuplicatesTimestamps(filtered)
            filtered
        })

    private def actionsOnInterval(context: ActivateContext, from: (Long, Int), to: (Long, Int), isRevert: Boolean) = {
        val actions = migrations(context)
            .filter(_.hasToRun(from._1, to._1))
            .map(e => if (!isRevert) e.upActions else e.downActions)
            .flatten
        actions
            .filter(_.hasToRun(from, to, isRevert))
    }

    private def execute(context: ActivateContext, actions: List[MigrationAction], isRevert: Boolean): Unit =
        for (action <- actions) {
            execute(context, action)
            context.transactional {
                val version = storageVersion(context)
                if (isRevert) {
                    if (action.number > 0) {
                        version.lastScript = action.migration.timestamp
                        version.lastAction = action.number - 1
                    } else {
                        version.lastScript = action.migration.timestamp - 1
                        version.lastAction = Integer.MAX_VALUE
                    }
                } else {
                    version.lastScript = action.migration.timestamp
                    version.lastAction = action.number
                }
            }
        }

    private def execute(context: ActivateContext, action: MigrationAction): Unit =
        action match {
            case e: StorageAction =>
                context.execute(e)
            case e: CustomScriptAction =>
                e.f()
        }

    private def verifyDuplicatesTimestamps(filtered: List[Migration]) = {
        val duplicates =
            filtered.filterNot(filtered.contains)
        if (duplicates.nonEmpty)
            throw new IllegalStateException("Duplicate migration timestamps " + duplicates.mkString(", "))
    }
}

@implicitNotFound("Can't find a EntityValue implicit converter. Maybe the column type is not supported.")
case class Column[T](name: String, specificTypeOption: Option[String])(implicit val m: Manifest[T], val tval: Option[T] => EntityValue[T]) {
    private[activate] def emptyEntityValue =
        tval(None)
}

abstract class ManualMigration(implicit context: ActivateContext) extends Migration {
    def timestamp = -2l
    override private[activate] def hasToRun(fromMigration: Long, toMigration: Long) =
        false
    def execute = Migration.execute(context, this)
    def revert = Migration.revert(context, this)
}

@implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
abstract class Migration(implicit val context: ActivateContext) {

    def timestamp: Long
    def name: String = getClass.getSimpleName()
    def developers: List[String] = List("not specified")

    private var number: Int = -1
    private var isCollectingActions = false

    def transactional[A](f: => A): A =
        if (isCollectingActions)
            throw new IllegalStateException("Do not use transactional blocks as migration actions, use customScripts.")
        else
            context.transactional(f)

    def nextNumber = {
        number += 1
        number
    }

    private var _actions = List[MigrationAction]()
    private def addAction[T <: MigrationAction](action: T) = {
        _actions ++= List(action)
        action
    }
    private def clear = {
        _actions = List[MigrationAction]()
        number = -1
    }
    private[activate] def upActions = {
        isCollectingActions = true
        try {
            clear
            up
            _actions.toList
        } finally
            isCollectingActions = false
    }
    private[activate] def downActions = {
        isCollectingActions = true
        try {
            clear
            down
            _actions.toList
        } finally
            isCollectingActions = false
    }
    private[activate] def hasToRun(fromMigration: Long, toMigration: Long) =
        timestamp > fromMigration && timestamp <= toMigration

    class ColumnDef {
        private var _definitions = List[Column[_]]()
        @implicitNotFound("Can't find a EntityValue implicit converter. Maybe the column type is not supported.")
        def column[T](name: String)(implicit m: Manifest[T], tval: Option[T] => EntityValue[T]) =
            buildColumn[T](name, None)
        def customColumn[T](name: String, customTypeName: String)(implicit m: Manifest[T], tval: Option[T] => EntityValue[T]) =
            buildColumn[T](name, Some(customTypeName))
        private[activate] def definitions =
            _definitions.toList
        private def buildColumn[T](name: String, customTypeNameOption: Option[String])(implicit m: Manifest[T], tval: Option[T] => EntityValue[T]) = {
            val column = Column[T](name, customTypeNameOption)
            _definitions ++= List(column)
            column
        }
    }

    private def entitiesMetadatas =
        EntityHelper.metadatas.filter(e =>
            e.entityClass != classOf[StorageVersion]
                && ActivateContext.contextFor(e.entityClass) == context)

    def createTableForAllEntities =
        IfNotExistsBag(entitiesMetadatas.map(createTableForEntityMetadata))

    def createTableForEntity[E <: Entity: Manifest] =
        createTableForEntityMetadata(EntityHelper.getEntityMetadata(erasureOf[E]))

    def createInexistentColumnsForAllEntities =
        entitiesMetadatas.map(createInexistentColumnsForEntityMetadata)

    def createInexistentColumnsForEntity[E <: Entity: Manifest] =
        createInexistentColumnsForEntityMetadata(EntityHelper.getEntityMetadata(erasureOf[E]))

    def createReferencesForAllEntities =
        IfNotExistsBag(entitiesMetadatas.map(createReferencesForEntityMetadata).flatten)

    def removeReferencesForAllEntities =
        IfExistsBag(entitiesMetadatas.map(removeReferencesForEntityMetadata).flatten)

    def createReferencesForEntity[E <: Entity: Manifest] =
        IfNotExistsBag(createReferencesForEntityMetadata(EntityHelper.getEntityMetadata(erasureOf[E])))

    def removeAllEntitiesTables = {
        val metadatas = entitiesMetadatas
        val tree = new DependencyTree(metadatas.toSet)
        for (metadata <- metadatas) {
            for (property <- metadata.persistentPropertiesMetadata if (property.name != "id")) {
                metadatas.find(_.entityClass == property.propertyType).map(depedencyMetadata =>
                    tree.addDependency(metadata, depedencyMetadata))
            }
        }
        val resolved =
            try {
                tree.resolve
            } catch {
                case e: CyclicReferenceException =>
                    // Let storage cry if necessary!
                    metadatas.toList
            }
        val actionList = resolved.map(metadata => {
            val mainTable = table(manifestClass(metadata.entityClass))
            val lists = metadata.persistentListPropertiesMetadata
            val removeLists = lists.map(list => {
                val (columnName, listTableName) = EntityPropertyMetadata.nestedListNamesFor(metadata, list)
                mainTable.removeNestedListTable(columnName, listTableName)
            })
            removeLists ++ List(mainTable.removeTable)
        }).flatten
        IfExistsWithCascadeBag(actionList)
    }

    private def createTableForEntityMetadata(metadata: EntityMetadata) = {
        val (normalColumns, listColumns) =
            metadata.persistentPropertiesMetadata.filter(!_.isTransient).
                partition(p => p.propertyType != classOf[List[_]] && p.propertyType != classOf[LazyList[_]])
        val ownerTable = table(manifestClass(metadata.entityClass))
        val mainAction =
            ownerTable.createTable(columns =>
                for (property <- normalColumns if (property.name != "id")) {
                    columns.column[Any](property.name)(manifestClass(property.propertyType), property.tval)
                })
        val nestedListsActions =
            listColumns.map { listColumn =>
                val tval = EntityValue.tvalFunction(listColumn.genericParameter, classOf[Object])
                val (columnName, listTableName) = EntityPropertyMetadata.nestedListNamesFor(metadata, listColumn)
                ownerTable.createNestedListTableOf(columnName, listTableName)(manifestClass(listColumn.genericParameter), tval)
            }
        IfNotExistsBag(List(mainAction) ++ nestedListsActions)
    }

    private def createInexistentColumnsForEntityMetadata(metadata: EntityMetadata) = {
        val tableInstance = table(manifestClass(metadata.entityClass))
        for (property <- metadata.persistentPropertiesMetadata if (property.name != "id"))
            tableInstance.addColumn(columnDef =>
                columnDef.column(property.name)(manifestClass(property.propertyType), property.tval)).ifNotExists
    }

    private def max(string: String, size: Int) =
        string.substring(0, (size - 1).min(string.length))

    private def shortConstraintName(tableName: String, propertyName: String) =
        max(tableName, 14) + "_" + max(propertyName, 15)

    private def createReferencesForEntityMetadata(metadata: EntityMetadata) =
        referencesForEntityMetadata(metadata)
            .map(reference =>
                table(manifestClass(metadata.entityClass)).addReference(reference._1, reference._2, reference._3)) ++
            nestedListsReferencesForEntityMetadata(metadata).map {
                reference => table(reference._1, reference._2).addReference("value", reference._3, reference._4)
            }

    private def referencesForEntityMetadata(metadata: EntityMetadata) =
        for (
            property <- metadata.persistentPropertiesMetadata;
            if (classOf[Entity].isAssignableFrom(property.propertyType) &&
                !property.propertyType.isInterface &&
                !Modifier.isAbstract(property.propertyType.getModifiers) &&
                context.storageFor(metadata.entityClass) == context.storageFor(property.propertyType.asInstanceOf[Class[Entity]]))
        ) yield (property.name, EntityHelper.getEntityName(property.propertyType), shortConstraintName(metadata.name, property.name))

    private def nestedListsReferencesForEntityMetadata(metadata: EntityMetadata) =
        for (
            property <- metadata.persistentListPropertiesMetadata;
            if (classOf[Entity].isAssignableFrom(property.genericParameter) &&
                !property.genericParameter.isInterface &&
                !Modifier.isAbstract(property.genericParameter.getModifiers) &&
                context.storageFor(metadata.entityClass) == context.storageFor(property.genericParameter.asInstanceOf[Class[Entity]]))
        ) yield (
            EntityPropertyMetadata.nestedListNamesFor(metadata, property)._2,
            context.storageFor(metadata.entityClass),
            EntityHelper.getEntityName(property.genericParameter),
            shortConstraintName(metadata.name, property.name))

    private def removeReferencesForEntityMetadata(metadata: EntityMetadata) =
        referencesForEntityMetadata(metadata).map(reference =>
            table(manifestClass(metadata.entityClass)).removeReference(reference._1, reference._2, reference._3)) ++
            nestedListsReferencesForEntityMetadata(metadata).map {
                reference => table(reference._1, reference._2).removeReference("value", reference._3, reference._4)
            }

    case class Table(name: String, storage: Storage[_]) {

        def createTable(definitions: ((ColumnDef) => Unit)*): CreateTable = {
            val columns = new ColumnDef()
            definitions.foreach(_(columns))
            addAction(CreateTable(Migration.this, storage, nextNumber, name, columns.definitions))
        }
        def removeTable: RemoveTable =
            addAction(RemoveTable(Migration.this, storage, nextNumber, name))

        def renameTable(newName: String): RenameTable =
            addAction(RenameTable(Migration.this, storage, nextNumber, name, newName))

        def addColumn(definition: (ColumnDef) => Unit): AddColumn = {
            val columns = new ColumnDef()
            definition(columns)
            addAction(AddColumn(Migration.this, storage, nextNumber, name, columns.definitions.onlyOne))
        }

        def renameColumn(oldName: String, newColumn: (ColumnDef) => Unit): RenameColumn = {
            val columns = new ColumnDef()
            newColumn(columns)
            val definition = columns.definitions.head
            addAction(RenameColumn(Migration.this, storage, nextNumber, name, oldName, definition))
        }

        def removeColumn(columnName: String): RemoveColumn =
            addAction(RemoveColumn(Migration.this, storage, nextNumber, name, columnName))

        def modifyColumnType(column: (ColumnDef) => Unit): ModifyColumnType = {
            val columns = new ColumnDef()
            column(columns)
            val definition = columns.definitions.head
            addAction(ModifyColumnType(Migration.this, storage, nextNumber, name, definition))
        }

        def addIndex(columnName: String, indexName: String, unique: Boolean = false): AddIndex =
            addAction(AddIndex(Migration.this, storage, nextNumber, name, columnName, indexName, unique))
        def removeIndex(columnName: String, indexName: String, unique: Boolean = false): RemoveIndex =
            addAction(RemoveIndex(Migration.this, storage, nextNumber, name, columnName, indexName, unique))

        def addReference(columnName: String, referencedTable: Table, constraintName: String): AddReference =
            addReference(columnName, referencedTable.name, constraintName)
        private[activate] def addReference(columnName: String, referencedTable: String, constraintName: String): AddReference =
            addAction(AddReference(Migration.this, storage, nextNumber, name, columnName, referencedTable, constraintName))

        def removeReference(columnName: String, referencedTable: Table, constraintName: String): RemoveReference =
            removeReference(columnName, referencedTable.name, constraintName)
        private[activate] def removeReference(columnName: String, referencedTable: String, constraintName: String): RemoveReference =
            addAction(RemoveReference(Migration.this, storage, nextNumber, name, columnName, referencedTable, constraintName))

        def createNestedListTableOf[T](columnName: String, listTableName: String)(implicit m: Manifest[T], tval: Option[T] => EntityValue[T]) = {
            val addColumnAction = addColumn(_.column[List[T]](columnName)(manifest[List[T]], EntityValue.toListEntityValueOption(_)(manifest[T], tval)))
            val addTableAction = addAction(CreateListTable(Migration.this, storage, nextNumber, name, listTableName, Column("value", None)))
            IfNotExistsBag(List(addColumnAction, addTableAction))
        }
        def removeNestedListTable(columnName: String, listTableName: String) = {
            val removeColumnAction = removeColumn(columnName)
            val removeTableAction = addAction(RemoveListTable(Migration.this, storage, nextNumber, name, listTableName))
            IfExistsBag(List(removeColumnAction, removeTableAction))
        }
    }
    def table[E <: Entity: Manifest]: Table =
        table(EntityHelper.getEntityName(erasureOf[E]), context.storageFor(erasureOf[E]))
    def table(name: String, storage: Storage[_]): Table =
        Table(name, storage)
    def table(name: String): Table =
        Table(name, context.storage)
    def customScript(f: => Unit) =
        addAction(CustomScriptAction(this, nextNumber, () => context.transactional(f)))
    def up: Unit
    def down: Unit = {
        val revertActions = upActions.map(_.revertAction)
        _actions = List[MigrationAction]()
        revertActions.reverse.foreach(addAction(_))
    }

    override def toString =
        "" + timestamp + " - " + name + " - Developers: " + developers.mkString(",")
}

