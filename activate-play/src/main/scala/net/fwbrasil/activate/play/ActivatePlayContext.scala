package net.fwbrasil.activate.play

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.memory.TransientMemoryStorage
import play.Play
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.ActivateProperties
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory

trait ActivatePlayContext extends ActivateContext {

    val storage = buildStorage

    super.delayedInit({})

    def refreshStorage =
        Reflection.set(this, "storage", buildStorage)

    private def defaultProperties =
        Map("factory" -> "net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory")

    private def keyMappings =
        Map("jdbcDriver" -> "driver",
            "password" -> "pass")

    private def dialects =
        SqlIdiom.dialectsMap.keys.map(name => (name.replace("Dialect", "").toLowerCase, name)).toMap

    protected def buildStorage: Storage[_] =
        if (configuration.isEmpty)
            new TransientMemoryStorage
        else {
            val properties = new ActivateProperties(None, None, getProperty)
            StorageFactory.fromProperties(properties)
        }

    protected def getProperty(name: String): Option[String] = {
        val key = keyMappings.getOrElse(name, name)
        configuration.flatMap(_.getString(key))
            .orElse(defaultProperties.get(key))
            .orElse {
                if (name == "dialect")
                    findDialectFromUrl
                else
                    None
            }
    }

    protected def findDialectFromUrl =
        getProperty("url").flatMap(url => dialects.filterKeys(url.contains).headOption.map(_._2))

    protected def configuration =
        play.api.Play.maybeApplication.flatMap(_.configuration.getConfig("db").flatMap(_.getConfig(dbName)))

    protected def dbName = "default"
}