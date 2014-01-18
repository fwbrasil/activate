package net.fwbrasil.activate.storage.relational.async

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.mysql.pool.MySQLConnectionFactory

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect

trait AsyncMySQLStorage extends AsyncSQLStorage[MySQLConnection] {
    val dialect: mySqlDialect = mySqlDialect
}

object AsyncMySQLStorageFactory extends StorageFactory {
    class AsyncMySQLStorageFromFactory(val properties: Map[String, String]) extends AsyncMySQLStorage {

        def configuration =
            new Configuration(
                username = properties("user"),
                host = properties("host"),
                password = Some(properties("password")),
                database = Some(properties("database")))

        lazy val objectFactory = new MySQLConnectionFactory(configuration)
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new AsyncMySQLStorageFromFactory(properties)
}