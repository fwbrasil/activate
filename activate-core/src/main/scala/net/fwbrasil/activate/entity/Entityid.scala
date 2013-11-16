package net.fwbrasil.activate.entity

import org.joda.time.DateTime
import java.util.Date
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.ActivateContext

trait EntityId {
    this: Entity =>
        
    type ID <: AnyRef
    
    val id: ID
}

trait UUID {
    this: Entity =>

    type ID = String

    final val id: String = null

    def creationTimestamp = UUIDUtil timestamp id.substring(0, 35)
    def creationDate = new Date(creationTimestamp)
    def creationDateTime = new DateTime(creationTimestamp)
}

trait CustomID[T] {
    this: Entity =>
    
    type ID = T
    
    val id: T
}