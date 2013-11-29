package net.fwbrasil.activate

import net.fwbrasil.activate.entity.BaseEntity

package object play {
    
    @deprecated("EntityData is deprecated, please use net.fwbrasil.activate.entity.map.MutableEntityMap", "1.5")
    type EntityData[T <: BaseEntity] = net.fwbrasil.activate.entity.map.MutableEntityMap[T]
}