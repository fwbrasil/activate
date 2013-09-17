package net.fwbrasil.activate.storage

import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.Var

trait SnapshotableStorage[T] {
    this: Storage[T] =>

    def snapshot: Unit

}