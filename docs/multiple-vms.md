# MULTIPLE VMS #
Activate uses memory efficiently by maintaining soft references for the entities that are loaded from the storage.
To use this approach in an environment with multiple virtual machines using the same storage, there is the Optimistic Offline Locking.

This is a [widely known](http://martinfowler.com/eaaCatalog/optimisticOfflineLock.html) pattern to deal with concurrency. It uses an entity version information to detect conflicts using the underlying storage.

The propose of offline locking is to detect transaction conflicts at commit time (optimistic locking), then Activate can reload the entity information and retry the transaction if it is possible.

For a detailed explanation of the offline locking internals, please refer to the [Architecture Documentation](http://activate-framework.org/documentation/architecture/#multiple_vms).


## CONFIGURATION ##
There are two system properties to configure the offline locking:

- **activate.offlineLocking.enable**
	
	If this parameter is true, the offline locking is enabled. Default: false.

- **activate.offlineLocking.validateReads**

	If this parameter is true, the offline locking also validate all transactions reads against the storage version information. Default: false.

**IMPORTANT**: It is necessary to create the version column for all entities. It must be a Long value. Example migration:

``` scala
class ExampleMigration extends Migration {
def timestamp = 201208191834l
def up = {
table[MyEntity]
.addColumn(
_.column[Long]("version"))
}
}
```