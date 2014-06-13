package net.fwbrasil.activate.storage.relational.async

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import com.twitter.finagle.client.DefaultPool
import com.twitter.util.TimeConversions._
import com.twitter.finagle.exp.MysqlClient
import net.fwbrasil.activate.ActivateContext
import com.twitter.finagle.exp.MysqlStackClient

object FinagleMySQLStorageFactory extends StorageFactory {
    class FinagleMySQLStorageFromFactory(val getProperty: String => Option[String]) extends FinagleMySQLStorage {

        val client =
            new MysqlClient(MysqlStackClient.configured(poolConfiguration))
                .withCredentials(getProperty("user").get, getProperty("password").get)
                .withDatabase(getProperty("database").get)
                .newRichClient(getProperty("host").get)

        def poolConfiguration = {
            var config = DefaultPool.Param.default
            getProperty("poolMaxQueueSize").map { value =>
                config = config.copy(maxWaiters = value.toInt)
            }
            getProperty("poolMaxObjects").map { value =>
                config = config.copy(high = value.toInt)
            }
            getProperty("poolMinObjects").map { value =>
                config = config.copy(low = value.toInt)
            }
            getProperty("poolMaxIdle").map { value =>
                config = config.copy(idleTime = value.toLong millis)
            }
            config
        }
    }
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new FinagleMySQLStorageFromFactory(getProperty)
}
