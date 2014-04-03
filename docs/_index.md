# Index

## [Get started](/docs/get-started.md)

Activate is a framework to persist objects in Scala.

## [Persistence Context](/docs/persistence-context.md)

To use Activate, first you have to define a Persistence Context.

## [Entity](/docs/entity.md)

To define a persistent entity you just have to extend the Entity trait.

## [Transaction](/docs/transaction.md)

All entity creation, modification, read or query must be executed inside a transaction, otherwise a RequiredTransactionException will be thrown.

## [Validation](/docs/validation.md)

Activate provides a [Design by Contract (DbC)](http://en.wikipedia.org/wiki/Design_by_contract) mechanism to achieve validation.

## [Query](/docs/query.md)

Activate queries are consistent, even with entities created in the current transaction, so a new entity can be returned in a query.

## [Migration](/docs/migration.md)

Migration is the storage schema evolution mechanism provided by Activate.

## [Multiple VMs](/docs/multiple vms.md)

Activate uses memory efficiently by maintaining soft references for the entities that are loaded from the storage.

## [Mass statement](/docs/mass statement.md)

Activate supports mass update/delete statements.

## [Play framework](/docs/play framework.md)

The “**activate-play**” component has some classes to facilitate the Activate usage with the [Play Framework](http://www.playframework.com/) 2.2.

## [Lift framework](/docs/lift framework.md)

The “**activate-lift**” module has the EntityForm that provides a easy way to handle lift entity forms.

## [Spray Json](/docs/spray json.md)

The [spray-json](https://github.com/spray/spray-json) integration is a easy way to manipulate entities from/to json.

## [Architecture](/docs/architecture.md)

Activate is a Software Transactional Memory with efficient memory usage and pluggable persistence.
