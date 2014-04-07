package net.fwbrasil.activate.storage.relational.async

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.mysql.pool.MySQLConnectionFactory
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import com.github.mauricio.async.db.pool.PoolConfiguration
import scala.concurrent.Future

trait AsyncMySQLStorage extends AsyncSQLStorage[MySQLConnection] {
    val dialect: mySqlDialect = mySqlDialect
}

object AsyncMySQLStorageFactory extends StorageFactory {
    class AsyncMySQLStorageFromFactory(val getProperty: String => Option[String]) extends AsyncMySQLStorage {

        def configuration =
            new Configuration(
                username = getProperty("user").get,
                host = getProperty("host").get,
                port = getProperty("port").map(_.toInt).getOrElse(3306),
                password = getProperty("password"),
                database = getProperty("database"))

        lazy val objectFactory = new MySQLConnectionFactory(configuration)

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
        
        override def await[R](future: Future[R]) =
            throw new IllegalStateException("Don't block!")
    }
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new AsyncMySQLStorageFromFactory(getProperty)
}