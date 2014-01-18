package net.fwbrasil.activate.storage.relational.async

import net.fwbrasil.activate.storage.Storage
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import com.github.mauricio.async.db.Configuration
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.ActivateContext
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect

trait AsyncPostgreSQLStorage extends AsyncSQLStorage[PostgreSQLConnection] {
    val dialect: postgresqlDialect = postgresqlDialect
}

object AsyncPostgreSQLStorageFactory extends StorageFactory {
    class AsyncPostgreSQLStorageFromFactory(val properties: Map[String, String]) extends AsyncPostgreSQLStorage {

        def configuration =
            new Configuration(
                username = properties("user"),
                host = properties("host"),
                password = Some(properties("password")),
                database = Some(properties("database")))

        lazy val objectFactory = new PostgreSQLConnectionFactory(configuration)
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new AsyncPostgreSQLStorageFromFactory(properties)
}