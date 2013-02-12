package net.fwbrasil.activate

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Entity

trait PolyglotActivateContext extends ActivateContext {

    def additionalStorages: Map[Storage[_], Set[Class[Entity]]]

}