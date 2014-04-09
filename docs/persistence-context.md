# Persistence context

To use Activate, first you have to define a Persistence Context. It coordinates all the persistence needs, like transactions and the persistence lifecycle of entities.

**Important**: The context definition must be declared in a base package of the entities and migrations packages.
Example: com.app.myContext for com.app.model.MyEntity

Context must be a singleton, so it makes sense declare it as “object”. The name must be unique.

Examples:

**PostgreSQL**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
 
object postgresqlContext extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.postgresql.Driver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:postgresql://127.0.0.1/postgres"
        val dialect = postgresqlDialect
    }
}
```

**MySQL**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
 
object mysqlContext extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "com.mysql.jdbc.Driver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:mysql://127.0.0.1/myDatabase"
        val dialect = mySqlDialect
    }
}
```

**Oracle**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.oracleDialect
 
object oracleContext extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:oracle:thin:@localhost:1521:oracle"
        val dialect = oracleDialect
    }
}
```

**H2**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.h2Dialect
 
object h2Context extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.h2.Driver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:h2:mem:my_database;DB_CLOSE_DELAY=-1"
        val dialect = h2Dialect
    }
}
```

**Derby**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.derbyDialect
 
object h2Context extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.apache.derby.jdbc.EmbeddedDriver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:derby:memory:my_database;create=true"
        val dialect = derbyDialect
    }
}
```

**HsqlDB**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.hsqldbDialect
 
object h2Context extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.hsqldb.jdbcDriver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:hsqldb:mem:my_database"
        val dialect = hsqldbDialect
    }
}
```

**DB2**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.db2Dialect
 
object db2Context extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "com.ibm.db2.jcc.DB2Driver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:db2://localhost:50000/MYDB"
        val dialect = db2Dialect
    }
}
```

**SQL Server**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.sqlServerDialect
 
object sqlServerContext extends ActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "net.sourceforge.jtds.jdbc.Driver"
        val user = "USER"
        val password = "PASS"
        val url = "jdbc:jtds:sqlserver://localhost:49503/mydb"
        val dialect = sqlServerDialect
    }
}
```

**MongoDB**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.mongo.MongoStorage
 
object mongoContext extends ActivateContext {
    val storage = new MongoStorage {
        val host = "localhost"
        override val port = 27017
        val db = "dbName"
        override val authentication = Option("USER", "PASS")
    }
}
```

**Prevayler**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.prevayler.PrevaylerStorage
 
object prevaylerContext extends ActivateContext {
    val storage = new PrevaylerStorage
}
```

**Prevalent**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.prevalent.PrevalentStorage
 
object prevalentContext extends ActivateContext {
    val storage = new PrevalentStorage("someDirectory")
}
```

**Transient Memory**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.memory.TransientMemoryStorage
 
object memoryContext extends ActivateContext {
    val storage = new TransientMemoryStorage
}
```

**Async MongoDB**

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.mongo.async.AsyncMongoStorage
 
object asyncMongoContext extends ActivateContext {
    val storage = new AsyncMongoStorage {
        val host = "localhost"
        override val port = 27017
        val db = "dbName"
        override val authentication = Option("USER", "PASS")
    }
}
```

**Async PostgreSQL**

```
import net.fwbrasil.activate.ActivateContext
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import net.fwbrasil.activate.storage.relational.async.AsyncPostgreSQLStorage
 
object asyncPostgreSQLContext extends ActivateContext {
    val storage = new AsyncPostgreSQLStorage {
        def configuration =
            new Configuration(
                username = "user",
                host = "localhost",
                password = Some("password"),
                database = Some("database_name"))
        lazy val objectFactory = new PostgreSQLConnectionFactory(configuration)
    }
}
```

All storages implements a method called “**directAccess**” which provides direct access to the underlying database. Use this method carefully, if you modify the database content, the entities in memory could stay in an inconsistent state.

## System properties

It’s possible to define the storage based on system properties. For this context:

```
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.StorageFactory
 
object myContext extends ActivateContext {
    val storage = StorageFactory.fromSystemProperties("myContext")
}
```

These are the system properties:

**PostgreSQL**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=org.postgresql.Driver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:postgresql://127.0.0.1/postgres
activate.storage.myContext.dialect=postgresqlDialect
```

**MySQL**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=com.mysql.jdbc.Driver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:mysql://127.0.0.1/myDatabase
activate.storage.myContext.dialect=mySqlDialect
```

**Oracle**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=oracle.jdbc.driver.OracleDriver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:oracle:thin:@localhost:1521:oracle
activate.storage.myContext.dialect=oracleDialect
```

**H2**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=org.h2.Driver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:h2:mem:my_database;DB_CLOSE_DELAY=-1
activate.storage.myContext.dialect=h2Dialect
```

**Derby**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=org.apache.derby.jdbc.EmbeddedDriver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:derby:memory:my_database;create=true
activate.storage.myContext.dialect=derbyDialect
```

**HsqlDB**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=org.hsqldb.jdbcDriver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:hsqldb:mem:my_database
activate.storage.myContext.dialect=hsqldbDialect
```

**DB2**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=com.ibm.db2.jcc.DB2Driver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:db2://loacalhost:50000/MYDB
activate.storage.myContext.dialect=db2Dialect
```

**SQL Server**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory
activate.storage.myContext.jdbcDriver=net.sourceforge.jtds.jdbc.Driver
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
activate.storage.myContext.url=jdbc:jtds:sqlserver://localhost:49503/mydb
activate.storage.myContext.dialect=sqlServerDialect
```

**MongoDB**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.mongo.MongoStorageFactory
activate.storage.myContext.host=localhost
activate.storage.myContext.port=27017
activate.storage.myContext.db=dbName
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
```

**Prevayler**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.prevayler.PrevaylerMemoryStorageFactory
activate.storage.myContext.prevalenceDirectory=myPrevalenceDirectory
```

> The prevalence directory is optional. Default is “activate”.

**Prevalent**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.prevalent.PrevalentStorageFactory
activate.storage.myContext.directory=someDirectory
```

**Transient Memory**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.memory.TransientMemoryStorageFactory
```

**Async MongoDB**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.mongo.async.AsyncMongoStorageFactory
activate.storage.myContext.host=localhost
activate.storage.myContext.port=27017
activate.storage.myContext.db=dbName
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
```

**Async PostgreSQL**

```
activate.storage.myContext.factory=net.fwbrasil.activate.storage.relational.async.AsyncPostgreSQLStorageFactory
activate.storage.myContext.host=localhost
activate.storage.myContext.database=dbName
activate.storage.myContext.user=USER
activate.storage.myContext.password=PASS
```

## Custom cache configuration

Activate has a cache called LiveCache that uses [soft references](http://docs.oracle.com/javase/7/docs/api/java/lang/ref/SoftReference.html) for the entities. They that are cleaned as soon the virtual machine needs memory. See the [Architecture](http://activate-framework.org/documentation/architecture/) documentation for more information. This behavior should be sufficient for most of the use cases, but there is a mechanism to customize the caching configuration.

It is possible to modify the LiveCache to use [weak references](http://docs.oracle.com/javase/7/docs/api/java/lang/ref/WeakReference.html) for example:

```
object myContext extends ActivateContext {
    val storage = ...
    override def liveCacheType =
        CacheType.weakReferences
}
```

And create custom caches per entity class:

```
object myContext extends ActivateContext {
    val storage = ...
    override def liveCacheType =
        CacheType.weakReferences
    override def customCaches =
        List(
            CustomCache[MyEntity](
                cacheType = CacheType.weakReferences,
                limitOption = Some(1000))
}
```

Entities that extend the MyEntity class will be hold in this cache at limit of 1000 instances.

These are the CustomCache parameters and default values:

```
case class CustomCache[E <: Entity: Manifest](
        cacheType: CacheType,
        transactionalCondition: Boolean = false,
        condition: E => Boolean = (e: E) => true,
        limitOption: Option[Long] = None,
        expiration: Duration = Duration.Inf)
```

**cacheType**

The type of references to be used by the cache.

**transactionalCondition**

Indicates if the condition block must run inside a transaction.

**condition**

A condition that entities must satisfy to be in the cache.

**limitOption**
Limit of instances to be in the cache

**expiration**

How long an instance should be maintained by the cache

An interesting use case could be for example to maintain in-memory entities of premium users of an application:

```
object myContext extends ActivateContext {
    val storage = ...
    override def customCaches =
        List(
            CustomCache[User](
                cacheType = CacheType.hardReferences,
                transactionalCondition = true,
                condition = (u: User) => u.isPremium
                limitOption = Some(1000))
}
    
```

The CustomCaches are consistent with new end modified entities by transactions.


## Context useful methods and values

- **def reinitializeContext: Unit**

    Reinitializes the context, cleaning the LiveCache.

- **def currentTransaction: Transaction**

    Returns the current transaction.

- **def reloadEntities(ids: Set[String]): Unit**

    Forces the specified entities to reload from the database.

- **val retryLimit: Int**

    Indicates the limit number of tentatives to retry a transaction. Default: 3000.


## Polyglot context

Activate has support to do polyglot persistence. It is possible to determine the storage that must persist each entity class. For example, an entity from a relational database can have a property that is an entity persisted at a non-relational database.

For synchronized transactions, at commit time, the [two phase](http://en.wikipedia.org/wiki/Two-phase_commit_protocol) protocol is used to persist the information in the multiple storages. For non-transactional storages, there is a mechanism that implements a transactional behavior on the application level to do the two phase commit.

For asynchronous transactions, the commit process is executed using a extended transaction model. It is a non-blocking approach for distributed transactions. At commit time, each modification performed during the in memory STM transaction (creates/updates/deletes) is categorized per storage. After, modifications are sent to each storage. If an error occur during the commit process, the already modified storages are rollbacked by the application by sending a set of operations that reverts the performed modifications. For an insert, the rollback operation is a delete. For a delete, an insert. And for an update, an update to the old value. This approach can produce concurrency inconsistencies if there is more than one VM running the persistence context. This is the price to have non-blocking distributed transactions.

To configure the polyglot persistence, override the “additionalStorages” method:

```
object polyglotContext extends ActivateContext {
 
    val postgre = fromSystemProperties("postgre")
    val mysql = fromSystemProperties("mysql")
    val mongo = fromSystemProperties("mongo")
 
    val storage = postgre
 
    override def additionalStorages = Map(
        mongo -> Set(classOf[MyMongoEntity]),
        mysql -> Set(classOf[MyMysqlEntity]))
}
```

By default, Activate will persist all entities in the default storage, in this case postgre. The “additionalStorages” method returns a map indicating witch entities must be persisted in other storages. In this case, the “MyMongoEntity” will be persisted in mongo and the “MySqlEntity” will be persisted in mysql.

**IMPORTANT**: A query cannot run using joins with entities that are persisted in different storages.

For implementation details, see the [architecture documentation](http://activate-framework.org/documentation/architecture/#polyglot).

## Multiple contexts

It’s possible to have multiple contexts in the same virtual machine. For this scenario, it’s necessary to override the “contextEntities” val in your contexts. Example:

`override val contextEntities = Some(List(classOf[MyEntity]))`

If contextEntities is None (this is the default), Activate assumes that the context accepts all entities. An error will occur if more than one context accepts an entity.

It’s not possible to have relations between entities from different contexts.

## Memory index

The persistence context can have in-memory indexes that provide fast access to entities. The indexes are consistent with new and modified entities by transactions in the same VM. It is possible to enable the optimistic locking with read validation so the cache can be updated for modified entities in other VMs too. For now, the cache can’t be updated automatically for new entities created in other VMs. This limitation should be resolved on the 1.5 version.

```
object myContext extends ActivateContext {
    val storage = ...
    val indexPersonByName = memoryIndex[Person].on(_.name)
}
```

To use the index, just call the method “get”:

```
transactional {
    val list: LazyList[Person] = indexPersonByName.get("John")
}
```

The memory index holds references to the keys, the “name” in the case, and to the corresponding list of entity ids. The method “get” returns a LazyList that loads the entities when needed.

An example with a tuple key:

```
val indexPersonByNameAndAge =
    memoryIndex[Person].on(person => (person.name, person.age))
val list: LazyList[Person] = indexPersonByNameAndAge.get("John", 30)
```

## JDBC

To use jdbc, import the “**activate-jdbc**” component in your sbt or maven project.

There are three [jdbc](http://en.wikipedia.org/wiki/JDBC) storage implementations inside package **net.fwbrasil.activate.storage.relational**:

- **PooledJdbcRelationalStorage**

    Uses a c3p0 connection pool to maintain connections. 
    Properties based factory: PooledJdbcRelationalStorageFactory

- **DataSourceJdbcRelationalStorage**

    Uses a container datasource to get connections.
    Properties based factory: DataSourceJdbcRelationalStorageFactory

- **SimpleJdbcRelationalStorage**

    Open connections on demand. It’s recommended only for embedded systems.
    Properties based factory: SimpleJdbcRelationalStorageFactory

Available dialects inside package **net.fwbrasil.activate.storage.relational.idiom**:

- **postgresqlDialect**
- **oracleDialect**
- **mySqlDialect**
- **h2Dialect**
- **db2Dialect**
- **derbyDialect**
- **hsqldbDialect**

It’s easy to implement new idioms. [Open an issue](https://github.com/fwbrasil/activate/issues/new) if you have a new dialect suggestion.

The “**directAccess**” method returns a “**java.sql.Connection**“.

## MongoDB

To use [MongoDB](http://www.mongodb.org/), import the “**activate-mongo**” component in your sbt or maven project.

The storage class is **net.fwbrasil.activate.storage.mongo.MongoStorage**.

Mongo is the performance winner between activate supported storages. It has three main restrictions:

1. Only simple queries (no joins) can be performed.
2. It ignores “add column”, “add reference” and “remove reference” migration actions.
3. Activate adds a good level of consistency to the usage with Mongo, but it cannot guarantee that always will be consistent, due the database characteristics.

The “**directAccess**” method returns a “**com.mongodb.DB**“.

## Async MongoDB

To use Async MongoDB, import the “**activate-mongo-async**” component in your sbt or maven project.

This storage provides blocking and non-blocking queries and transactions. The [reactivemongo](http://reactivemongo.org/) project is used as the underlying database driver.

It is possible to override the “**val executionContext: ExecutionContext**” and “**val defaultTimeout: Int**” values that are used to perform the blocking operations.

The “**directAccess**” method returns a “**reactivetemongo.api.DefaultDB**“.

## Async PostgreSQL

To use async PostgreSQL, import the “**activate-jdbc-async**” component in your sbt or maven project.

This storage provides blocking and non-blocking queries and transactions. The [postgresql-async](https://github.com/mauricio/postgresql-async) project is used as the underlying database driver.

It is possible to override the “**val executionContext: ExecutionContext**” and “**val defaultTimeout: Int**” values that are used to perform the blocking operations.

The “**directAccess**” method returns a “**Future[PostgreSQLConnection]**“.

## Prevayler

To use Prevayler, import the “**activate-prevayler**” component in your sbt or maven project.

[Prevayler](http://prevayler.org/) uses the prevalence paradigm, maintaining all the data in memory, so your data must fit in memory.

The storage class is **net.fwbrasil.activate.storage.prevayler.PrevaylerStorage**.

There are three constructors:

- **new PrevaylerStorage()**
 
    Creates prevayler using the directory “activate”.

- **new PrevaylerStorage(prevalenceDirectory: String)**
    
    Creates prevayler using the prevalenceDirectory.

- **new PrevaylerStorage(factory: PrevaylerFactory)**
    
    Creates prevayler using the factory configurations.

The “pure” prevayler synchronizes the transactions, running only one per time. But Activate can run multiple transactions in parallel and just at the end of the commit time, it uses prevayler which synchronizes the persistence.

For now, Activate don’t maintain indexes in the prevalent system, so queries can be slow with a big amount of data.

This storage ignores all migration actions except the custom ones.

The “**directAccess**” method returns a “**org.prevayler.Prevayler**“.

## Prevalent

To use the PrevalentStorage, import the “**activate-prevalent**” component in your sbt or maven project.

This is a custom implementation using the same prevalent paradigm of Prevayler. It has high performance, since uses memory mapped files.

The storage class is **net.fwbrasil.activate.storage.prevalent.PrevalentStorage**.

There is a constructor with default parameters:

``` scala
class PrevalentStorage(
directory: String,
serializer: Serializer = javaSerializer,
fileSize: Int = 10 * 1000 * 1000,
bufferPoolSize: Int = Runtime.getRuntime.availableProcessors)
```

**directory**
The directory used to maintain the persistence files.

**serializer**
The serializer to do snapshots. The transaction serialization is made by the prevalentTransactionSerializer direct to memory.

**fileSize**
Max size of the transaction log files.

**bufferPoolSize**
Number of memory buffers to reuse.

This storage ignores all migration actions except the custom ones.

The “**directAccess**” method returns a “**net.fwbrasil.activate.storage.prevalent.PrevalentStorageSystem**“.

## Transient memory

The transient memory storage is in the “**activate-core**” component.

The storage class is **net.fwbrasil.activate.storage.memory.TransientMemoryStorage**.

Transient Memory is designed to be used only to run tests.
It fulfills all the persistence needs, including queries and transactions, but doesn’t persist the data.

The “**directAccess**” method returns a “**net.fwbrasil.activate.storage.memory.TransientMemoryStorageSet**“.
