package net.fwbrasil.activate.entity

import net.fwbrasil.activate.entity.id.CustomID
import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.entity.id.GeneratedID

trait Entity extends BaseEntity with UUID
trait EntityWithCustomID[ID] extends BaseEntity with CustomID[ID]
trait EntityWithGeneratedID[ID] extends BaseEntity with GeneratedID[ID]