# Index

## [Get started](/activate-docs/get-started.md)

Activate is a framework to persist objects in Scala.

## [Persistence Context](/activate-docs/persistence-context.md)

To use Activate, first you have to define a Persistence Context.

## [Entity](/activate-docs/entity.md)

To define a persistent entity you just have to extend the Entity trait.

## [Transaction](/activate-docs/transaction.md)

All entity creation, modification, read or query must be executed inside a transaction, otherwise a RequiredTransactionException will be thrown.

## [Validation](/activate-docs/validation.md)

Activate provides a [Design by Contract (DbC)](http://en.wikipedia.org/wiki/Design_by_contract) mechanism to achieve validation.

## [Query](/activate-docs/query.md)

Activate queries are consistent, even with entities created in the current transaction, so a new entity can be returned in a query.

## [Test support](/activate-docs/test-support.md)

The **activate-test** module provides an infrastructure to ease writing tests with Activate.

## [Migration](/activate-docs/migration.md)

Migration is the storage schema evolution mechanism provided by Activate.

## [Multiple VMs](/activate-docs/multiple-vms.md)

Activate uses memory efficiently by maintaining soft references for the entities that are loaded from the storage.

## [Mass statement](/activate-docs/mass-statement.md)

Activate supports mass update/delete statements.

## [Play framework](/activate-docs/play-framework.md)

The “**activate-play**” component has some classes to facilitate the Activate usage with the [Play Framework](http://www.playframework.com/) 2.2.

## [Lift framework](/activate-docs/lift-framework.md)

The “**activate-lift**” module has the EntityForm that provides a easy way to handle lift entity forms.

## [Spray Json](/activate-docs/spray-json.md)

The [spray-json](https://github.com/spray/spray-json) integration is a easy way to manipulate entities from/to json.

## [Architecture](/activate-docs/architecture.md)

Activate is a Software Transactional Memory with efficient memory usage and pluggable persistence.
