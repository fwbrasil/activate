package net.fwbrasil.activate.storage.memory

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.enumerationAsScalaIterator
import scala.collection.JavaConversions.mapAsScalaConcurrentMap
import scala.collection.TraversableOnce.flattenTraversableOnce
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.util.Reflection.NiceObject
import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap

class BasePrevalentStorageSystem extends Serializable {
    val contents = new HashMap[String, HashMap[Entity#ID, Entity]] with SynchronizedMap[String, HashMap[Entity#ID, Entity]]
    def add(entity: Entity) =
        entitiesMapFor(entity.niceClass) += entity.id -> entity
    def remove(entityClass: Class[Entity], entityId: Entity#ID) =
        entitiesMapFor(entityClass) -= entityId
    def remove(entity: Entity): Unit =
        remove(entity.niceClass, entity.id)
    def entities =
        contents.values.map(_.values).flatten
    def entitiesListFor(name: String) =
        contents.keys.filter(className => EntityHelper.getEntityName(ActivateContext.loadClass(className)) == name)
            .map(contents(_).values)
            .flatten
    def entitiesMapFor(entityClass: Class[Entity]) = {
        contents.get(entityClass.getName).getOrElse {
            this.synchronized {
                contents.getOrElseUpdate(entityClass.getName, new HashMap[Entity#ID, Entity] with SynchronizedMap[Entity#ID, Entity])
            }
        }
    }
}
