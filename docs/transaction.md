# Transaction

All entity creation, modification, read or query must be executed inside
a transaction, otherwise a RequiredTransactionException will be thrown.
Usage example:

``` scala
transactional {
    val person = new Person("John")
    person.name = "John2"
    println(person.name)
}
```

**Important**: Activate uses optimistic locking assuming that the
transaction does not have side effects. If you have transaction side
effects, like IO, use the manual transaction control below.

## Propagations

Activate uses the RadonSTM, which provides a powerful in-memory transaction control. It’s possible to define propagations:

``` scala
transactional(required) {
    val person = new Person("John")
    person.name = "John2"
    println(person.name)
}
```

NOTE: If you don’t specify propagation, “required” is the default.

These are the supported propagations:

- **required**

    Support a current transaction, create a new one if none exists.

- **nested**

    Execute within a nested transaction if a current transaction exists,
otherwise throws a RequiredTransactionException.

- **mandatory**

    Support a current transaction, throw a RequiredTransactionException if
none exists.

- **never**

    Execute non-transactionally, throw a NotSupportedTransactionException
if a transaction exists.

- **notSupported**

    Execute non-transactionally, suspend the current transaction if one
exists.

- **requiresNew**

    Create a new transaction, suspend the current transaction if one
exists.

- **supports**

    Support a current transaction, execute non-transactionally if none
exists.

## Manual transaction control

The client code can hold a transaction instance and manually control its lifecycle. Using this approach a ConcurrentTransactionException can be thrown if a conflict with another transaction occurs.

``` scala
val myTransaction = new Transaction
try {
    transactional(myTransaction) {
        val person = new Person("John")
        person.name = "John2"
        println(person.name)
    }
    myTransaction.commit
} catch {
    case e =>
        myTransaction.rollback
        throw e
}
```

It’s possible to call more than one transactional block using the same transaction instance.

Defining a transaction propagation:

``` scala
val myTransaction = new Transaction
try {
    transactional(myTransaction, nested) {
        val person = new Person("John")
        person.name = "John2"
        println(person.name)
    }
    myTransaction.commit
} catch {
    case e =>
        myTransaction.rollback
        throw e
}
```

## Async transactions

Activate supports async transactions:

``` scala
val aFuture: Future[Person] =
    asyncTransactional {
        new Person("John")
    }
```

The transactional block will run inside a future.

If the storage supports non-blocking operations (async postgresql or async mongo), the transaction commit will perform non-blocking async operations. Otherwise, the commit process will run in a blocking future and a warning message is emitted:

`Storage does not support non-blocking async operations. Async operations will run inside blocking futures.`

It is also possible to define a chain of transactional futures using
**asyncTransactionalChain**:

``` scala
val aFuture: Future[Unit] =
    asyncTransactionalChain { implicit ctx =>
        asyncAll[Person].map {
            person => person.delete
        }
    }
```

The “implicit ctx” parameter is a TransactionalExecutionContext. It is a execution context that perform executions inside a transaction.

**Important**: Do not compose parallel futures using a TransactionalExecutionContext. Otherwise, a IllegalStateException(“ExclusiveThreadLocal: value is bound to another thread.”) will be thrown.
