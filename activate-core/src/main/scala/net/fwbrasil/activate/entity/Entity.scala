package net.fwbrasil.activate.entity

import net.fwbrasil.activate.entity.id.CustomID
import net.fwbrasil.activate.entity.id.UUID

trait Entity extends BaseEntity with UUID
trait EntityWithCustomID[ID] extends BaseEntity with CustomID[ID]