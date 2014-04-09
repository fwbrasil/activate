# VALIDATION #
Activate provides a [Design by Contract (DbC)](http://en.wikipedia.org/wiki/Design_by_contract) mechanism to achieve validation.


## PRE-CONDITIONS ##
It’s possible to define a condition that must be fulfilled before method execution:

``` scala
override def delete =
    preCondition(name != "Undeletable Person") {
        super.delete
    }
```
Using method parameters inside a pre-condition:

``` scala
def modifyName(newName: String) =
    preCondition(name != newName) {
        name = newName
    }
```
Defining the pre-condition name:

``` scala
def modifyName(newName: String) =
    preCondition(name != newName, "preConditionName") {
        name = newName
    }
```
If a pre-condition is not satisfyied, Activate throws a PreCondidionViolationException.


## POST-CONDITIONS ##
It’s possible to define a condition that must be fulfilled after method execution:

``` scala
def modifyName(newName: String) = {
        name = newName
    } postCondition (name == newName)
```
Post-condition name:

``` scala
def modifyName(newName: String) = {
    name = newName
} postCondition (name == newName, "postConditionName")
```
Using the method return:

``` scala
def namePlusA = {
    name + "a"
} postCondition(result => result == name + "a")
```
If a post-condition is not satisfyied, Activate throws a PostCondidionViolationException.


## INVARIANTS ##
Invariants are validation predicates that are verified in the entity lifecycle. They are special instance methods:

``` scala
def invariantNameMustNotBeEmpty = invariant {
    name != null && name.nonEmpty
}
```
NOTE: The invariant name is the method name.

It’s a good practice to prefix the invariant method name with “invariant”, thus the client code can easily know the entity invariants.

Passing error params:

``` scala
def invariantNameMustNotBeEmpty =
    invariant(errorParams = List(name)) {
        name != null && name.nonEmpty
    }
```
If a pre-condition is violated, Activate throws an InvariantViolationException.


## INVARIANTS LIFECYCLE ##
It’s possible to define validation options globally, by transaction, by thread or by entity instance. The available options are:

- **onCreate**

	Validate invariants after instance creation. If there is an invalid invariant at the end of the constructor, Activate deletes the entity and throws an InvariantViolationException.

- **onWrite**

	Validate invariants on attribute modifications using a nested transaction. If an invariant fail, the nested transaction aborts and modification is not propagated to outer transaction.

- **onRead**

	Validate invariants on attribute read. Don’t permit to read attributes from invalid entities, throwing an InvariantViolationException.

- **onTransactionEnd**

	Validate invariants on transaction end.

By default, Activate has onCreate and onWrite global options.

To define custom options for an entity instance, override entity “**validationOptions**” method:

``` scala
override protected def validationOptions =
    Some(Set(EntityValidationOption.onTransactionEnd))
```

Methods of **net.fwbrasil.activate.entity.EntityValidation** object:

- **def removeAllCustomOptions: Unit**

	Remove all custom options (does not include entities overridden validationOptions)

- **def addGlobalOption(option: EntityValidationOption): Unit**

	**def removeGlobalOption(option: EntityValidationOption): Unit**

	**def setGlobalOptions(options: Set[EntityValidationOption]): Unit**

	**def getGlobalOptions: Set[EntityValidationOption]**

	Methods to define global options (default is onCreate and onWrite).

- **def addTransactionOption(option: EntityValidationOption): Unit**

	**def removeTransactionOption(option: EntityValidationOption): Unit**

	**def setTransactionOptions(options: Set[EntityValidationOption]): Unit**

	**def getTransactionOptions(implicit ctx: ActivateContext): Option[Set[EntityValidationOption]]**

	Methods to define transaction options (default is no options – None).

- **def addThreadOption(option: EntityValidationOption): Unit**

	**def removeThreadOption(option: EntityValidationOption): Unit**

	**def setThreadOptions(options: Set[EntityValidationOption]): Unit**

	**def getThreadOptions(implicit ctx: ActivateContext): Option[Set[EntityValidationOption]]**

	Methods to define thread options (default is no options – None).

You can also validate an entity at any moment by calling the “**validate**” method or the invariant methods.
