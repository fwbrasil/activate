package net.fwbrasil.activate.util.uuid

import java.util.Date

trait Idable {

    import language.postfixOps

    val id = UUIDUtil generateUUID
    def creationTimestamp = UUIDUtil timestamp id
    def creationDate = new Date(creationTimestamp)

}
