# Activate Persistence Framework

https://codeship.io/projects/6791ccc0-c291-0131-1750-5afae8528f01/status

![Activate splash](http://activate-framework.org/wp-content/uploads/2012/04/activateslider4.jpg)

Documentation ([index](/activate-docs/_index.md)):

- [Get started](/activate-docs/get-started.md)
- [Persistence Context](/activate-docs/persistence-context.md)
- [Entity](/activate-docs/entity.md) 
- [Transaction](/activate-docs/transaction.md)
- [Validation](/activate-docs/validation.md)
- [Query](/activate-docs/query.md)
- [Test support](/activate-docs/test-support.md)
- [Migration](/activate-docs/migration.md)
- [Multiple VMs](/activate-docs/multiple-vms.md)
- [Mass statement](/activate-docs/mass-statement.md)
- [Play framework](/activate-docs/play-framework.md)
- [Lift framework](/activate-docs/lift-framework.md)
- [Spray Json](/activate-docs/spray-json.md)
- [Architecture](/activate-docs/architecture.md)

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

