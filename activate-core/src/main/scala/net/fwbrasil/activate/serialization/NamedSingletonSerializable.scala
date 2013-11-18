package net.fwbrasil.activate.serialization

import scala.collection.mutable.{ HashMap => MutableHashMap, ListBuffer, HashSet => MutableHashSet }
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.ActivateContext
import java.util.concurrent.ConcurrentHashMap

trait NamedSingletonSerializable extends java.io.Serializable {

    private[activate] def name: String

    NamedSingletonSerializable.registerInstance(this)

    protected def writeReplace(): AnyRef =
        new NamedSingletonSerializableWrapper(this)

}

class NamedSingletonSerializableWrapper(instance: NamedSingletonSerializable) extends java.io.Serializable {
    val name = instance.name
    val clazz = instance.getClass
    protected def readResolve(): Any =
        NamedSingletonSerializable.instance(clazz, name)
}

object NamedSingletonSerializable {
    val instances =
        new MutableHashMap[String, MutableHashMap[String, NamedSingletonSerializable]]

    private[this] def instancesMapOf[T: Manifest] =
        synchronized {
            val key = erasureOf[T].getName
            var map = instances.getOrElseUpdate(key, new MutableHashMap[String, NamedSingletonSerializable])
            map.asInstanceOf[MutableHashMap[String, T]]
        }

    def instancesOf[T <: NamedSingletonSerializable: Manifest] =
        synchronized {
            import scala.collection.JavaConversions._
            val ret = MutableHashSet[T]()
            for ((clazz, instancesMap) <- instances; if (erasureOf[T].isAssignableFrom(ActivateContext.loadClass(clazz))))
                ret ++= instancesMap.values.asInstanceOf[Iterable[T]]
            ret
        }

    def instance(clazz: Class[_], name: String) =
        synchronized {
            instances(clazz.getName)(name)
        }

    def registerInstance[T <: NamedSingletonSerializable](instance: T) =
        synchronized {
            implicit val m = manifestClass[T](instance.getClass)
            val map = instancesMapOf[T]
            val option = map.get(instance.name)
            map += (instance.name -> instance)
        }

}