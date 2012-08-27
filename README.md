
Activate Persistence Framework

Official site with documentation:
[http://activate-framework.org](http://activate-framework.org "http://activate-framework.org")

INTRODUCTION
============

Activate is a framework to persist objects in Scala. It is a STM (Software Transactional Memory) durable, with pluggable persistence. Its core is the RadonSTM, which provides a powerful mechanism for controlling transactions in memory, analogous to the transactions of databases, to do optimistic concurrency control. The durability of transactions (persistence) is pluggable and can use persistence in different paradigms such as relational (JDBC), prevalence (Prevayler) and non-relational (MongoDB).

BENEFITS
========

The main benefits of the framework are:

- Atomic, consistent, isolated and durable transactions. You can use entities without worrying about concurrency issues.
- Entities are always consistent in memory and in the persistence layer. For example, if rollback occurs, entities in memory stay consistent.
- Transaction propagation control, including nested transactions.
- Entities are lazy loaded and initialized automatically when needed.
- Queries are type-safe and consistent, even with objects created in the current transaction. Therefore, an entity created in the same transaction may be returned in a query.
- The available memory is used efficiently, minimizing the conversation with the storage and maximizing performance.

BUILD
=====

Use sbt 0.11.2 to build Activate. Use the command "eclipse" to generate the eclipse project.
To run tests, you have to provide the databases instances for the contexts defined on the net.fwbrasil.activate.ActivateTest.contexts method of the activate-tests project.

LICENSE
=======

The code is licensed under LGPL.
