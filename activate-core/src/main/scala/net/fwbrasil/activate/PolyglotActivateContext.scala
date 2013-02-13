package net.fwbrasil.activate

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Entity

trait PolyglotActivateContext extends ActivateContext {

    protected def entity[E <: Entity: Manifest] =
        manifest[E].runtimeClass.asInstanceOf[Class[Entity]]

    override def storageFor[E <: Entity](entityClass: Class[E]): Storage[Any] =
        additionalStoragesByEntityClasses.getOrElse(
            entityClass.asInstanceOf[Class[Entity]],
            storage.asInstanceOf[Storage[Any]])

    private lazy val additionalStoragesByEntityClasses =
        (for ((storage, classes) <- additionalStorages.asInstanceOf[Map[Storage[Any], Set[Class[Entity]]]]) yield classes.map((_, storage))).flatten.toMap

    val additionalStorages: Map[Storage[_], Set[Class[Entity]]]

}