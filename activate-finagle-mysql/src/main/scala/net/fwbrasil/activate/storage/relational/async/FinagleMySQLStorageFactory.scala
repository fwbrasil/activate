package net.fwbrasil.activate.storage.relational.async

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import com.twitter.finagle.client.DefaultPool
import com.twitter.util.TimeConversions._
import com.twitter.util.Duration
import com.twitter.finagle.exp.mysql.{Client, Request, Result}
import com.twitter.finagle.exp.{MySqlClientTracingFilter, Mysql}
import com.twitter.finagle.{ServiceFactory, Stack, StackBuilder, Stackable}
import com.twitter.finagle.client.{DefaultPool, StackClient}
import net.fwbrasil.activate.ActivateContext

object FinagleMySQLStorageFactory extends StorageFactory {
    class FinagleMySQLStorageFromFactory(val getProperty: String => Option[String]) extends FinagleMySQLStorage {

        val client = Mysql.Client(new StackBuilder[ServiceFactory[Request, Result]](StackClient.newStack).result)
                .configured(poolConfiguration)
                .withCredentials(getProperty("user").get, getProperty("password").get)
                .withDatabase(getProperty("database").get)
                .newRichClient(getProperty("host").get)

        def poolConfiguration = {
            val maxWaiters = getProperty("poolMaxQueueSize").map(_.toInt).getOrElse(Int.MaxValue)
            val high = getProperty("poolMaxObjects").map(_.toInt).getOrElse(Int.MaxValue)
            val low = getProperty("poolMinObjects").map(_.toInt).getOrElse(0)
            val idleTime = getProperty("poolMaxIdle").map(_.toLong.millis).getOrElse(Duration.Top)
          DefaultPool.Param(
            bufferSize = 0,
            high = high,
            maxWaiters = maxWaiters,
            low = low,
            idleTime = idleTime)
        }
    }
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new FinagleMySQLStorageFromFactory(getProperty)
}
