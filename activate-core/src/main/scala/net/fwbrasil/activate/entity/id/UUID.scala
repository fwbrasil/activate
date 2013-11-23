package net.fwbrasil.activate.entity.id

import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.entity.Entity
import org.joda.time.DateTime
import java.util.Date

trait UUID {
    this: Entity =>

    type ID = String

    final val id: String = null

    def creationTimestamp = UUIDUtil timestamp id.substring(0, 35)
    def creationDate = new Date(creationTimestamp)
    def creationDateTime = new DateTime(creationTimestamp)
}

object uuidGenerator extends IdGenerator[Entity with UUID] { 
    def nextId(entityClass: Class[_]) = {
        val uuid = UUIDUtil.generateUUID
        val classId = EntityHelper.getEntityClassHashId(entityClass)
        uuid + "-" + classId
    }
}