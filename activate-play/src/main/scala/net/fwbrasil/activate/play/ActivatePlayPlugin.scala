package net.fwbrasil.activate.play

import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.ActivateContext
import play.api.Plugin
import net.fwbrasil.activate.serialization.NamedSingletonSerializable
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityEnhancer
import net.fwbrasil.activate.entity.EntityMetadata
import net.fwbrasil.activate.util.Reflection
import play.Play

class ActivatePlayPlugin(app: play.Application) extends Plugin {

    override def onStart =
        ActivateContext.setClassLoader(Play.application.classloader)
}