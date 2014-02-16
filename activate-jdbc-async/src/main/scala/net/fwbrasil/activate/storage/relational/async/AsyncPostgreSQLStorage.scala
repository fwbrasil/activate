package net.fwbrasil.activate.storage.relational.async

import net.fwbrasil.activate.storage.Storage
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import com.github.mauricio.async.db.Configuration
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.ActivateContext
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import com.github.mauricio.async.db.pool.PoolConfiguration

trait AsyncPostgreSQLStorage extends AsyncSQLStorage[PostgreSQLConnection] {
    val dialect: postgresqlDialect = postgresqlDialect
}

object AsyncPostgreSQLStorageFactory extends StorageFactory {
    class AsyncPostgreSQLStorageFromFactory(val getProperty: String => Option[String]) extends AsyncPostgreSQLStorage {

        def configuration =
            new Configuration(
                username = getProperty("user").get,
                host = getProperty("host").get,
                password = getProperty("password"),
                database = getProperty("database"))

        lazy val objectFactory = new PostgreSQLConnectionFactory(configuration)

        override def poolConfiguration = {
            var config = PoolConfiguration.Default
            getProperty("poolMaxQueueSize").map { value =>
                config = config.copy(maxQueueSize = value.toInt)
            }
            getProperty("poolMaxObjects").map { value =>
                config = config.copy(maxObjects = value.toInt)
            }
            getProperty("poolMaxIdle").map { value =>
                config = config.copy(maxIdle = value.toInt)
            }
            config
        }
    }
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new AsyncPostgreSQLStorageFromFactory(getProperty)
}