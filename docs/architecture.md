# ARCHITECTURE #
Activate is a Software Transactional Memory with efficient memory usage and pluggable persistence. This architecture documentation has as its target explain the Activate internals.


## SOFTWARE TRANSACTIONAL MEMORY ##
The Activate core is a STM implementation in Scala called [RadonSTM](https://github.com/fwbrasil/radon-stm/). From the point of view of Radon, Activate is a plugin that provides transaction durability. STM is a in-memory concurrency control mechanism. It uses optimistic locking to achieve transaction isolation, dealing with concurrent access and modification.

The usual approach to deal with concurrency is to use pessimistic locking, avoiding concurrent access. The resource is locked in a certain point and other transactions can not access it, awaiting for the end of the locking transaction.

The optimistic approach does not lock resources during transaction execution. At the end, it validates all reads and writes, determining if the transaction is valid or not. If it is in an invalid state, there are two options: abort with an exception or retry to execute it. The retry is a valid option if the transaction has not side effects, like IO for example. To retry, the transaction is just cleaned and executed again, since it is isolated.


## TRANSACTIONAL UNIT ##
Lets take as an example a bank account entity:

![account-entity-1](/docs/img/account-entity-1.png)

As it is possible to observe, the balance variable has two parts: the identity (it is a balance) and a value (the balance value at certain time). Activate uses this two parts to create a indirection that permits each entity variable to be a STM transactional unit.

![account-entity-2](/docs/img/account-entity-2.png)

All the concurrency control is done in the identity. In the RadonSTM this transactional unit is called “Ref“. Activate specializes this type with the class “Var“.


## ENTITY ENHANCE ##
At persistence context initialization, all entities classes are enhanced. A simple class like:

``` scala
class Person(var name: String) extends Entity
```
At runtime, is transformed in:

``` scala
class Person(var name: Var[String]) extends Entity
```
Each entity field is transformed into a STM transactional unit. Entity fields assignments and reads are enhanced too. It’s not necessary to enhance the entity “client” classes, since in Scala there aren’t public fields and access is done by accessor methods. So, only local entity field access done by accessors needs to be enhanced.

It is not necessary to use a javaagent or custom classloader to have the entities enhanced. It is a question of class loading order, since JVM class loading is lazy. Unless there is a misusage of Activate, the persistence context class will be always loaded before any entity class. For example:

``` scala
object myContext extends ActivateContext{
    val storage = StorageFactory.fromSystemProperties("myStorage")
}
import myContext._
 
class Person(var name: String) extends Entity
object myMain extends App{
    transactional {
        new Person("John")
    }
} 
```
MyContext will always be loaded first than the Person class, in this case at the persistence context method “transactional” call. At context load, it is used a class called [EntityEnhancer](https://github.com/fwbrasil/activate/blob/master/activate-core/src/main/scala/net/fwbrasil/activate/entity/EntityEnhancer.scala) that scan the classpath using the [Reflections](http://code.google.com/p/reflections/) framework looking for all entity classes. After the scan, it enhance each entity class. After enhance, it loads each enhanced class.

The Person class should be loaded when its constructor is called (new Person(“John”)), but at that point the class is already loaded by the EntityEnhancer with its variables enhanced as transactional units.

The Activate enhance is very smooth. It does not difficult debug and supports complex entity hierarchies, including traits.


## EXAMPLE SCENARIO ##
An example on how Activate deals with two concurrent bank transactions using transactional units as entities fields. Observe that the transaction execution and retries are non-bloking.

![slide-1-638.jpg](/docs/img/slide-1-638.jpg?cb=1353364238 slide-1-638.jpg)


## TRANSACTIONAL CONTEXT ##
The transaction methods and control structures are provided by the RadonSTM transactional context. It is the basis for the [ActivateContext](https://github.com/fwbrasil/activate/blob/master/activate-core/src/main/scala/net/fwbrasil/activate/ActivateContext.scala) and has two important structures:

[TransactionClock](https://github.com/fwbrasil/radon-stm/blob/master/src/main/scala/net/fwbrasil/radon/transaction/time/TransactionClock.scala)
A clock to determine transaction start and end timestamps. It is implemented using an AtomicLong, incremented each time that is used. It guarantees that transaction start/end timestamps are unique.

[TransactionManager](https://github.com/fwbrasil/radon-stm/blob/master/src/main/scala/net/fwbrasil/radon/transaction/TransactionManager.scala)
The transaction manager has a [ExclusiveThreadLocal](https://github.com/fwbrasil/radon-stm/blob/master/src/main/scala/net/fwbrasil/radon/util/ExclusiveThreadLocal.scala) that determines the current transaction. The thread local is filled each time that a transaction is activated in a transaction [propagation](https://github.com/fwbrasil/radon-stm/blob/master/src/main/scala/net/fwbrasil/radon/transaction/Propagation.scala) triggered by a transactional block. The ExclusiveThreadLocal guarantees that the transaction is active in only one thread per time.


## THE COMMIT TIME ##
The conflict detection is based on the comparison of the transaction start timestamp with snapshots read and write timestamps. These are the main rules to determine if a transaction is inconsistent:

- If there is a read on a Ref that was modified by another transaction after the transaction start timestamp.
- If there is a write on a Ref that was read by another transaction after the transaction start timestamp.

There are some optimizations for read only transactions and another more subtle validations.

At commit time, there are locks to assure that other transactions can not make the current transaction commit validation inconsistent. Locks are fine grained on each entity variable, using a ReentrantReadWriteLock according the nature of the variable usage (read or write).

The retry mechanism can produce many retries in an extreme scenario. There is retryLimit variable defined in the transactional context to limit how many retries are allowed. The default is 3000. An example overriding it to 100:

``` scala
object myContext extends ActivateContext{
    val storage = StorageFactory.fromSystemProperties("myStorage")
    override val retryLimit = 100
}
```
## DATABASE TRANSACTION ##
During the STM transaction execution there is not a correspondent database transaction. There are database trips to perform queries and lazy entities initialization, done with individual transactions.

Activate doesn’t maintain a database transaction during the STM transaction because it should create databases locks that brings a blocking behavior incompatible with the non-blocking optimistic approach.

Only at commit time there is a database transaction, if it is supported.


## EFFICIENT MEMORY USAGE ##
A key point to a durable STM is to guarantee that exists only one transactional unit representing one data in the database, like the name of a Person. If there are more than one transactional unit representing the same data, the STM concurrency control properties are no longer valid.

Activate implements an in-memory repository called [LiveCache](https://github.com/fwbrasil/activate/blob/master/activate-core/src/main/scala/net/fwbrasil/activate/cache/live/LiveCache.scala). It assures that there is only one instance of an entity loaded in the memory. The approach to achieve this behavior is to use soft references to all loaded entities from the database.

For example, if two threads use the same entity, they will use the same object instance. All modifications will be isolated by transactions in a non-blocking way and the consistency will be assured at commit time. So, you do not have to worry about concurrency issues.

Thru the LiveCache soft references, Activate can efficiently use the available virtual machine memory. For example, if there is enough memory, all entities can stay in memory increasing performance by reducing the conversation with the database.

Soft references are also the best way to decide which entities stays in memory. The garbage collector has a great algorithm do determine the most used entities that are better to maintain in memory. Using this approach it is not necessary to create magical algorithms using expiration time, number os objects, etc.


## QUERIES AND LAZY LOADING ##
All Activate queries are executed by the LiveCache. It runs queries in memory and merges the result from the database query execution. For PrevaylerStorage and TransientMemoryStorage, the query is run using all in memory entities. For the other storages, LiveCache runs queries only for new and “dirty” entities. “Dirty” entities means entities that are modified by the current transaction.

When an entity is selected by a query, Activate loads only its ID. If the entity is already in memory, it is returned. If the entity is not in memory, LiveCache creates a lazy entity. The lazy entity has no other loaded variables than the ID. At the first variable usage, the entity is initialized. If there is a variable that is another entity, the same approach is used.


## MULTIPLE VMS ##
As Activate has efficient memory usage, entities are not reloaded from the database unless the garbage collector clears them or the application restarts. If more than one VM uses the same storage, the commit validation mechanism can not detect conflicts between transactions from different contexts/VMs.

To use Activate in this scenario, there is the Optimistic Offline Locking mechanism. Its role is to detect at commit time if there is a transaction conflict between transactions of different VMs. If there is a conflict, Activate reloads the entities information and retry the transaction if it is possible.

The in-memory STM conflict detection occurs at the entity instance **property** granularity. For offline locking the conflict detection is at entity **instance** granularity.

This is a [widely known](http://martinfowler.com/eaaCatalog/optimisticOfflineLock.html) pattern to deal with concurrency. Each entity has a “Long” value called “version”. Every time that a transaction uses an entity, at commit time the entity storage version is compared to the its in-memory version information. If the in-memory information is equal to the storage information, the transaction modifications are persisted and the version information is incremented. If the in-memory version diverges from the storage, the commit process is aborted, the entity is reloaded from the storage and the transaction is retried if it possible.

By default, the version information is used only for entity modifications. To activate the same mechanism for entities reads, it is necessary to set the “activate.offlineLocking.validateReads” property.

Please refer to the [Multiple VMs](http://activate-framework.org/documentation/multiple-vms/) documentation to learn how to configure the offline locking.


## POLYGLOT PERSISTENCE ##
It is possible to have more than one storage in the same persistence context. For example, a Person entity can be persisted in Mongo and have an attribute Contact that is an entity persisted in a relational storage. As the Activate transaction runs in-memory, during the transaction execution the storages are used only to get data that is not already in-memory, by running queries against the storage. For now, it is not possible to run queries that uses entities persisted in different storages.

At commit time, Activate identifies what are the storages for each transaction modification and a two-phase commit is used to persist the transaction. In phase one, each storage receives all modifications (updates, deletes, inserts) and returns a commit handle. If all storages respond with success, the persistence context goes to phase two and send a commit message to each storage.

Activate supports non-transactional storages that can not respond the first phase with a commit handle, since there isn’t a transaction. If the Activate transaction must be rollbacked because of a storage failure, the storages that do not respond with a commit handle are rollbacked by sending a group of modifications that reverts the modifications sent at phase one. This approach with non-transactional storages can lead to concurrency inconsistencies, but this is an expected behavior for this type of storages.

Storages receive the two-phase commit instructions in this order:

1. The “default” storage
2. Transactional storages
3. In-memory storages
4. Schemaless storages
5. Other storages

See the [polyglot context](http://activate-framework.org/documentation/persistence-context/#polyglot_context) documentation to usage instructions.


## STORAGES ##
Activate supports relational and non-relational databases. It is very easy to implement a new storage support, since it has only to execute queries and, at commit time, update values.

When using ACID storages, Activate keeps full consistency guarantee. When using non ACID databases (for now there is only MongoDB), the user can expect that eventually some data will be inconsistent. Activate adds a very high consistency level on top of non ACID databases, but eventually, in special when using database eventual consistency with multiple VMs, there should be inconsistencies like stale reads.

All concurrency tests are successfully executed, without inconsistencies, in top of non distributed MongoDB instance. The fact that exists only one transactional unit representing each of the storage data also assures that the same data is not updated in the database at the same time by concurrent threads. Thats why Activate adds a high level of consistency on top of non ACID databases. But for example it is not possible to assure that, if the server goes down, all transactions will be recovered, since MongoDB don’t maintain a file sync to persist it data by default.

If data is modified in the database directly, Activate will not see the modification if the entity is already in memory. It is necessary to reinitialize the application or call the persistence context “reinitializeContext” method. It will clean all entities that are in memory.
