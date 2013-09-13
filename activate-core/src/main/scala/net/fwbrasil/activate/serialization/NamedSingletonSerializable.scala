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
        NamedSingletonSerializable.instances.get(clazz.getName)(name)
}

object NamedSingletonSerializable {
    val instances =
        new ConcurrentHashMap[String, MutableHashMap[String, NamedSingletonSerializable]] 

    private[this] def instancesMapOf[T: Manifest] = {
        val key = erasureOf[T].getName
        var map = instances.get(key)
        if(map == null) {
            map = new MutableHashMap[String, NamedSingletonSerializable]
            instances.put(key, map)
        }
        map.asInstanceOf[MutableHashMap[String, T]]
    }

    def instancesOf[T <: NamedSingletonSerializable: Manifest] = {
        import scala.collection.JavaConversions._
        val ret = MutableHashSet[T]()
        for ((clazz, instancesMap) <- instances; if (erasureOf[T].isAssignableFrom(ActivateContext.loadClass(clazz))))
            ret ++= instancesMap.values.asInstanceOf[Iterable[T]]
        ret
    }

    def registerInstance[T <: NamedSingletonSerializable](instance: T) = {
        implicit val m = manifestClass[T](instance.getClass)
        val map = instancesMapOf[T]
        val option = map.get(instance.name)
        //		if (option.isDefined && option.get != instance)
        //			warn("Duplicate singleton!")
        map += (instance.name -> instance)
    }

}