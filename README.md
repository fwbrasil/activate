# Activate Persistence Framework

![Activate splash](http://activate-framework.org/wp-content/uploads/2012/04/activateslider4.jpg)

Documentation ([index](/docs/_index.md)):

- [Get started](/docs/get-started.md)
- [Persistence Context](/docs/persistence-context.md)
- [Entity](/docs/entity.md) 
- [Transaction](/docs/transaction.md)
- [Validation](/docs/validation.md)
- [Query](/docs/query.md)
- [Test support](/docs/test-support.md)
- [Migration](/docs/migration.md)
- [Multiple VMs](/docs/multiple-vms.md)
- [Mass statement](/docs/mass-statement.md)
- [Play framework](/docs/play-framework.md)
- [Lift framework](/docs/lift-framework.md)
- [Spray Json](/docs/spray-json.md)
- [Architecture](/docs/architecture.md)

## Introduction

Activate is a framework to persist objects in Scala. It is a [STM (Software Transactional Memory)](http://en.wikipedia.org/wiki/Software_transactional_memory) durable, with pluggable persistence. Its core is the RadonSTM, which provides a powerful mechanism for controlling transactions in memory, analogous to the transactions of databases, to do [optimistic concurrency control](http://en.wikipedia.org/wiki/Optimistic_concurrency_control). The durability of transactions (persistence) is pluggable and can use persistence in different paradigms such as relational ([JDBC](http://en.wikipedia.org/wiki/Java_Database_Connectivity)), prevalence ([Prevayler](http://prevayler.org/)) and non-relational ([MongoDB](https://www.mongodb.org/)).

## Benefits

The main benefits of the framework are:

- Atomic, consistent, isolated and durable transactions. You can use entities without worrying about concurrency issues.
- Entities are always consistent in memory and in the persistence layer. For example, if rollback occurs, entities in memory stay consistent.
- Transaction propagation control, including nested transactions.
- Entities are lazy loaded and initialized automatically when needed.
- Queries are type-safe and consistent, even with objects created in the current transaction. Therefore, an entity created in the same transaction may be returned in a query.
- The available memory is used efficiently, minimizing the conversation with the storage and maximizing performance.

## Build

Use sbt 0.11.2 to build Activate. Use the command "eclipse" to generate the eclipse project.
To run tests, you have to provide the databases instances for the contexts defined on the net.fwbrasil.activate.ActivateTest.contexts method of the activate-tests project.

## License

The code is licensed under [LGPL](http://pl.wikipedia.org/wiki/GNU_Lesser_General_Public_License).

