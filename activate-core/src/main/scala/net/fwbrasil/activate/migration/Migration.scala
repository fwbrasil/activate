package net.fwbrasil.activate.migration

import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.entity.Entity
import java.util.Date
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.ActivateContext
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityMetadata
import java.lang.reflect.Modifier
import net.fwbrasil.activate.util.GraphUtil.DependencyTree
import net.fwbrasil.activate.util.GraphUtil.CyclicReferenceException
import scala.annotation.implicitNotFound
import scala.annotation.implicitNotFound

class StorageVersion(val contextName: String, var lastScript: Long, var lastAction: Int) extends Entity

object Migration {

	val storageVersionCache = MutableMap[String, StorageVersion]()

	def storageVersion(ctx: ActivateContext) = {
		import ctx._
		@ManualMigration
		class StorageVersionMigration extends Migration {
			val timestamp = 0l
			val name = "Initial database setup (StorageVersion)"
			val developers = List("fwbrasil")
			def up = {
				createTableForEntity[StorageVersion]
					.ifNotExists
			}
		}
		storageVersionCache.getOrElseUpdate(context.name, {
			val setupActions =
				(new StorageVersionMigration).upActions
			setupActions.foreach(Migration.execute(context, _))
			transactional {
				allWhere[StorageVersion](_.contextName :== context.name)
					.headOption
					.getOrElse(new StorageVersion(context.name, -1, -1))
			}
		})
	}

	def storageVersionTuple(ctx: ActivateContext) = {
		val version = storageVersion(ctx)
		ctx.transactional {
			(version.lastScript, version.lastAction)
		}
	}

	val migrationsCache = MutableMap[ActivateContext, List[Migration]]()

	def update(context: ActivateContext) =
		updateTo(context, Long.MaxValue)

	def updateTo(context: ActivateContext, timestamp: Long) =
		context.synchronized {
			execute(context, actionsOnInterval(context, storageVersionTuple(context), (timestamp, Int.MaxValue), false), false)
		}

	def revertTo(context: ActivateContext, timestamp: Long) =
		context.synchronized {
			execute(context, actionsOnInterval(context, (timestamp, Int.MaxValue), storageVersionTuple(context), true), true)
		}

	private def migrations(context: ActivateContext) =
		migrationsCache.getOrElseUpdate(context, {
			val result =
				Reflection.getAllImplementors(List(classOf[Migration], context.getClass), classOf[Migration])
					.filter(e => !Reflection.hasClassAnnotationInHierarchy(e, classOf[ManualMigration]) && !e.isInterface && !Modifier.isAbstract(e.getModifiers()))
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

	private def verifyDuplicatesTimestamps(filtered: List[net.fwbrasil.activate.migration.Migration]): Unit = {
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

@implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
abstract class Migration(implicit val context: ActivateContext) {

	def timestamp: Long
	def name: String
	def developers: List[String]
	private var number: Int = -1
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
		clear
		up
		_actions.toList
	}
	private[activate] def downActions = {
		clear
		down
		_actions.toList
	}
	private[activate] def hasToRun(fromMigration: Long, toMigration: Long) =
		timestamp > fromMigration && timestamp <= toMigration

	class Columns {
		private var _definitions = List[Column[_]]()
		@implicitNotFound("Can't find a EntityValue implicit converter. Maybe the column type is not supported.")
		def column[T](name: String, customTypeName: Option[String] = None)(implicit m: Manifest[T], tval: Option[T] => EntityValue[T]) = {
			val column = Column[T](name, customTypeName)
			_definitions ++= List(column)
			column
		}
		def definitions =
			_definitions.toList
	}

	private def entitiesMetadatas =
		EntityHelper.metadatas.filter(e =>
			e.entityClass != classOf[StorageVersion]
				&& ActivateContext.contextFor(e.entityClass) == context)

	def createTableForAllEntities =
		new IfNotExistsBag[CreateTable] {
			val actions = entitiesMetadatas.map(createTableForEntityMetadata)
		}

	def createTableForEntity[E <: Entity: Manifest] =
		createTableForEntityMetadata(EntityHelper.getEntityMetadata(erasureOf[E]))

	def createInexistentColumnsForAllEntities =
		entitiesMetadatas.map(createInexistentColumnsForEntityMetadata)

	def createInexistentColumnsForEntity[E <: Entity: Manifest] =
		createInexistentColumnsForEntityMetadata(EntityHelper.getEntityMetadata(erasureOf[E]))

	def createReferencesForAllEntities = new {
		val actions = entitiesMetadatas.map(createReferencesForEntityMetadata)
		def ifNotExists =
			actions.foreach(_.ifNotExists)
	}

	def removeReferencesForAllEntities = new {
		val actions = entitiesMetadatas.map(removeReferencesForEntityMetadata)
		def ifExists =
			actions.foreach(_.ifExists)
	}

	def createReferencesForEntity[E <: Entity: Manifest] =
		createReferencesForEntityMetadata(EntityHelper.getEntityMetadata(erasureOf[E]))

	def removeAllEntitiesTables = {
		val metadatas = entitiesMetadatas
		val tree = new DependencyTree(metadatas.toSet)
		for (metadata <- metadatas) {
			for (property <- metadata.propertiesMetadata) {
				metadatas.find(_.entityClass == property.propertyType).map(depedencyMetadata =>
					tree.addDependency(metadata, depedencyMetadata))
			}
		}
		val resolved =
			try {
				tree.resolve
			} catch {
				case e: CyclicReferenceException =>
					"Let storage cry if necessary!"
					metadatas.toList
				case other =>
					throw other
			}
		new IfExistsBag[RemoveTable] with CascadeBag[RemoveTable] {
			val actions = resolved.map(metadata => table(manifestClass(metadata.entityClass)).removeTable)
			override def ifExists = {
				super.ifExists
				this
			}
			override def cascade = {
				super.cascade
				this
			}
		}
	}

	private def createTableForEntityMetadata(metadata: EntityMetadata) =
		table(manifestClass(metadata.entityClass)).createTable(columns =>
			for (property <- metadata.propertiesMetadata) {
				columns.column[Any](property.name)(manifestClass(property.propertyType), property.tval)
			})

	private def createInexistentColumnsForEntityMetadata(metadata: EntityMetadata) =
		(table(manifestClass(metadata.entityClass)).addColumns(columns =>
			for (property <- metadata.propertiesMetadata)
				columns.column(property.name)(manifestClass(property.propertyType), property.tval))).ifNotExists

	private def max(string: String, size: Int) =
		string.substring(0, (size - 1).min(string.length))

	private def shortConstraintName(tableName: String, propertyName: String) =
		max(tableName, 14) + "_" + max(propertyName, 15)

	private def createReferencesForEntityMetadata(metadata: EntityMetadata) =
		table(manifestClass(metadata.entityClass)).addReferences(
			referencesForEntityMetadata(metadata): _*)

	private def referencesForEntityMetadata(metadata: EntityMetadata) =
		for (property <- metadata.propertiesMetadata; if (classOf[Entity].isAssignableFrom(property.propertyType) && !property.propertyType.isInterface && !Modifier.isAbstract(property.propertyType.getModifiers)))
			yield (property.name, EntityHelper.getEntityName(property.propertyType), shortConstraintName(metadata.name, property.name))

	private def removeReferencesForEntityMetadata(metadata: EntityMetadata) =
		table(manifestClass(metadata.entityClass)).removeReferences(
			referencesForEntityMetadata(metadata): _*)

	case class Table(name: String) {
		def createTable(definitions: ((Columns) => Unit)*) = {
			val columns = new Columns()
			definitions.foreach(_(columns))
			addAction(CreateTable(Migration.this, nextNumber, name, columns.definitions))
		}
		def renameTable(newName: String) =
			addAction(RenameTable(Migration.this, nextNumber, name, newName))
		def removeTable =
			addAction(RemoveTable(Migration.this, nextNumber, name))
		def addColumns(definitions: ((Columns) => Unit)*) = {
			val columns = new Columns()
			definitions.foreach(_(columns))
			new IfNotExistsBag[AddColumn] {
				val actions = columns.definitions.map(e => addAction(AddColumn(Migration.this, nextNumber, name, e)))
			}
		}
		def renameColumn(oldName: String, column: (Columns) => Unit) = {
			val columns = new Columns()
			column(columns)
			val definition = columns.definitions.head
			addAction(RenameColumn(Migration.this, nextNumber, name, oldName, definition))
		}
		def removeColumns(definitions: String*) =
			new IfExistsBag[RemoveColumn] {
				val actions = definitions.toList.map(e => addAction(RemoveColumn(Migration.this, nextNumber, name, e)))
			}
		def addIndexes(definitions: (String, String)*) =
			new IfNotExistsBag[AddIndex] {
				val actions = definitions.toList.map(e => addAction(AddIndex(Migration.this, nextNumber, name, e._1, e._2)))
			}
		def removeIndexes(definitions: (String, String)*) =
			new IfExistsBag[RemoveIndex] {
				val actions = definitions.toList.map(e => addAction(RemoveIndex(Migration.this, nextNumber, name, e._1, e._2)))
			}
		def addReferences(definitions: (String, String, String)*) = {
			new IfNotExistsBag[AddReference] {
				val actions = definitions.toList.map(e => addAction(AddReference(Migration.this, nextNumber, name, e._1, e._2, e._3)))
			}
		}
		def removeReferences(definitions: (String, String, String)*) = {
			new IfExistsBag[RemoveReference] {
				val actions = definitions.toList.map(e => addAction(RemoveReference(Migration.this, nextNumber, name, e._1, e._2, e._3)))
			}
		}
	}
	def table[E <: Entity: Manifest]: Table =
		table(EntityHelper.getEntityName(erasureOf[E]))
	def table(name: String): Table =
		Table(name)
	def customScript(f: => Unit) =
		addAction(CustomScriptAction(this, nextNumber, () => context.transactional(f)))
	def up: Unit
	def down: Unit = {
		val revertActions = upActions.map(_.revertAction)
		_actions = List[MigrationAction]()
		revertActions.foreach(addAction(_))
	}

	override def toString =
		"" + timestamp + " - " + name + " - Developers: " + developers.mkString(",")
}

