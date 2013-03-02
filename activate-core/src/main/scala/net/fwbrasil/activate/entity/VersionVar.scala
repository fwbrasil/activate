package net.fwbrasil.activate.entity

import java.util.{ HashMap => JHashMap }

object VersionVar {

    val isActive = true
    System.getProperty("activate.coordinator.optimisticOfflineLocking") == "true"
}