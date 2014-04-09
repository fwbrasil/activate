# ENTITY
To define a persistent entity you just have to extend the Entity trait. First, import from your persistence context:

``` scala
import myContext._
```

With this import, all methods and types to use Activate will be in the class scope.

Entities can be defined in just one line:
    
``` scala
class Person(var name: String) extends Entity
```

You can declare the attributes as val or var, where they are immutable or not.

Activate uses **Transparent Persistence** for all classes that extends the Entity trait directly or indirectly. This means that all instance creations and modifications will be automatically persistent. It’s unnecessary to use methods like “persist”, “save”, “merge”, etc.


## THE ENTITY TRAIT ##
The entity trait adds the following methods and values:

- **val id: String**

    An automatic generated timestamp based UUID added with the entity type information.

    It’s possible to recover the class using EntityHelper.getEntityClassFromId.

    It has 45 characters.

- **def delete: Unit** 

	Delete the entity instance

- **def deleteIfHasntReferences: Unit**

	Deletes the entity only if it hans’t references.

- **def deleteCascade: Unit**

	Deletes the entity and all cascade references to it.

- **def canDelete: Boolean**

	Indicates if the entity hasn’t references and can be deleted.

- **def isDeleted: Boolean**

	Indicates if the instance is deleted.

- **def isDirty: Boolean**

	Indicates if the entity is modified by the current transaction.

- **def creationTimestamp: Long**
	
	**def creationDate: java.util.Date**
	
	**def creationDateTime: org.joda.time.DateTime**
	
	Returns the instance creation date/timestamp. It’s not possible to perform queries using this methods. They extract the timestamp information from the entity UUID.

- **def references: Map[EntityMetadata, List[Entity]]**
	
	Returns a map with all entities that references the entity.

- **def originalValue[T](f: this.type => T): T**
	
	Returns the original value of the attribute before the transaction start.

The entity trait also overrides the **toString** method, generating a string representation based on the entity properties.


## ATTRIBUTES TYPES ##
Activate has support for the types listed below. If an unsupported type is used, Activate will produce an error on context startup.

Simple types:

- **scala.Int**
- **scala.Long**
- **scala.Boolean**
- **scala.Char**
- **scala.Float**
- **scala.Double**
- **scala.math.BigDecimal**
- **java.lang.String**

Other types:

- **scala.collection.immutable.List**

	MongoStorage, PrevaylerStorage and TransientMemoryStorage persist lists as “native” values.
	JdbcRelationalStorage persists lists in a separate list table. See [migration](http://activate-framework.org/documentation/migration/) for more information.

- **net.fwbrasil.activate.entity.LazyList**

	This is a special list type of entities. It uses lazy loading of its contents, avoiding hard references for the referenced entities. The persistence context has implicit conversions that allows its usage as normal lists.

- **scala.Enumeration**

	To use enumerations you must sublcass Val. Instead of “type MyEnum = Value”, use case “class MyEnum(name: String) extends Val(name)”.

- **java.util.Date**

- **java.util.Calendar**

- **scala.Array[Byte]**

	If you have to modify a date/calendar/byte array attribute, don’t change the actual instance. Produce a new one and set the attribute again. Otherwise, Activate will not track modifications.

- **org.joda.time.base.AbstractInstant**

	Support for joda time AbstractInstant subclasses: Instant, DateMidnight and DateTime.

- **net.fwbrasil.activate.entity.Entity**

	Entity attributes are persisted as references (id) in the storage.

- **scala.Serializable**

	Activate uses Serializable as the last option, other types have priority. Ensure that the serializable is immutable, otherwise Activate will not control the serializable internal modifications.

- **scala.Option**

	Support for “options” for any of the types listed above.


## CUSTOM TYPE ENCODERS ##
For the types not listed above, it is possible to create custom encoders that translate the value to a known type. For example, given the class:

``` scala
class CustomEncodedEntityValue(val i: Int) extends Serializable
```
Just create the encoder class and add it to the **same package or a sub package** of your persistence context:

``` scala
import myContext._
class CustomEncodedEntityValueEncoder
		extends Encoder[CustomEncodedEntityValue, Int] {
	def encode(value: CustomEncodedEntityValue) = value.i
	def decode(i: Int) = new CustomEncodedEntityValue(i)
}
```
## CUSTOM SERIALIZERS ##
Activate uses serialization as the last option to persist an entity attribute, if it is supported. It is possible to determine the serializer to be used by overriding the context default serializer:

``` scala
import net.fwbrasil.activate.serialization.xmlSerializer
object myContext extends ActivateContext {
	val storage = ...
	override protected val defaultSerializer = xmlSerializer
}
```
Provided serializer implementations:


net.fwbrasil.activate.serialization.xmlSerializer

net.fwbrasil.activate.serialization.jsonSerializer

net.fwbrasil.activate.serialization.javaSerializer

net.fwbrasil.activate.serialization.kryoSerializer


Additionally, is possible to define custom serializers by entity attribute:

``` scala
import net.fwbrasil.activate.serialization.xmlSerializer
import net.fwbrasil.activate.serialization.jsonSerializer
object myContext extends ActivateContext {
   	val storage = ...
	override protected def customSerializers = List(
		serialize[MyEntity](_.someAttribute) using jsonSerializer,
		serialize[OtherEntity](_.otherAttribute) using xmlSerializer)
}
```
## TRANSIENT ATTRIBUTES ##
It is possible to use transient entity attributes using the Scala @transient annotation. The value can be an unsupported type, since it will not be persisted to the storage.

``` scala
class MyEntity extends Entity {
	@transient val valThatWillNotBePersisted = "a"
	@transient var varThatWillNotBePersisted = "b"
}
```
The value can be lazy. It will be initialized every time that the entity is loaded from the storage and the attribute used:

``` scala
class MyEntity extends Entity {
	@transient lazy val attributeThatWillBeLazyInitializedAferEntityLoad = "a"
}
```
## ENTITY ALIAS ##
It is possible to define custom names for entities and its properties. The custom names define how Activate persist the information. Example:

``` scala
@Alias("PersonTable")
class Person(@Alias("personName") var name: String) extends Entity
```
## ENTITY SERIALIZATION ##
An entity can be serialized/deserialized and stays consistent. Activate serialize entities inside an envelope containing the entity id. When the entity is deserialized, it is recovered from the LiveCache or from the storage. If the entity is serialized during a transaction that holds a modification on it, if the transaction don’t commit, the deserialized entity will not have the modification.


## LIFECYCLE LISTENERS ##
It is possible to listen events of the entity lifecycle by overriding these methods on each entity class:

``` scala
protected def beforeConstruct: Unit
```
Called before the entity constructor body execution. **IMPORTANT**: This event should be overridden in very special cases, since the instance is in an inconsistent state.

``` scala
protected def afterConstruct: Unit
```
Called just after the constructor body execution.

``` scala
protected def beforeInitialize: Unit
```
Called before the entity being loaded from the database.

``` scala
protected def afterInitialize = {}
```
Called after the entity being loaded from the database.

``` scala
protected def beforeDelete = {}
```
Called before the “delete” method execution.

``` scala
protected def afterDelete = {}
```
Called after the “delete” method execution.


## PROPERTY LISTENERS ##
It is possible to define special methods to have callbacks when entity properties are changed:

``` scala
class Person(var name: String) extends Entity {
    def onNameChange = on(_.name).change {
        val old = originalValue(_.name)
        println(s"Name modified. Old: $old New: $name")
    }
}
```
The “originalValue” entity method can be very useful inside property listeners.

## ENTITY MAP ##
Since Activate uses the transparent persistence, it is not good to create entities that should not persisted. The EntityMap is a way to hold entity values and create/update entities when is needed. Example usage:

``` scala
var personMap = new EntityMap[Person]()
personMap = personMap.put(_.name)("John")
personMap.get(_.name) // Some("John")
personMap(_.name) // "John"
```
The EntityMap is immutable and there is a mutable alternative:

``` scala
val personMap = new MutableEntityMap[Person]()
personMap.put(_.name)("John")
personMap.get(_.name) // Some("John")
personMap(_.name) // "John"
```
Available methods to manipulate entities:

``` scala
def createEntity: Entity
```
Creates an entity instance with the entity map values.

``` scala
def createEntityUsingConstructor: Entity
```
Tries to create using a constructor. If there isn’t one and only one constructor matching a subset of the map values, an exception is thrown. If there are more than the properties necessary to call the constructor, the remaining will be set by update automatically after the constructor call.

``` scala
def updateEntity(id: String): Entity
```
Finds the entity by id and updates it with the values in the map. Throws a NoSuchElementException if the id is invalid.

``` scala
def tryUpdate(id: String): Option[Entity]
```
Tries to update the entity given the id, if the entity exists.

``` scala
def updateEntity(entity: E): Entity
```
Updates the entity instance with the values.

Note: There are asynchronous variants for the methods starting with the prefix “async”.
