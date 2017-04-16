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

- **val id: T**

    Each entity must have a primary id field. It is possible to use automatic generated ids or define custom ids. See [entity id](/activate-docs/entity.md#entity-id).

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

## ENTITY ID ##

### UUID

By default, Activate provides an automatic id generation mechanism using UUIDs and the entity type information.

``` scala
class MyEntity extends Entity
```

This entity has a String id with 45 characters. It’s possible to recover the class using EntityHelper.getEntityClassFromId.

### CUSTOM ID

It is possible to use custom ids by extending from EntityWithCustomID:

``` scala
class MyEntity(id: Int) extends EntityWithCustomID[Int]
```

Using this approach, for each entity instantiation it is necesary to provide the id.

### CUSTOM GENERATED ID

The EntityWithGeneratedID trait provides automatic custom generated ids.

``` scala
class MyEntity extends EntityWithGeneratedID[Int]
```

It is necessary to have in the classpath an id generator:

``` scala
import net.fwbrasil.activate.entity.id.IdGenerator

class MyEntityIdGenerator extends IdGenerator[MyEntity] {
	def nextId = ...
}
```

You need to provide a concrete implementation for the nextId method. There are two default implementations using sequences:

``` scala
import net.fwbrasil.activate.entity.id.SegmentedIdGenerator

class MyEntityIdGenerator 
		extends SegmentedIdGenerator[MyEntity](mySequence)

class MyEntityIdGenerator 
		extends SequencedIdGenerator[MyEntity](mySequence)
```

The **SegmentedIdGenerator*** uses segments to reduce the lock contention during the id generation. It should be used if you don't need strictly sequenced ids.

The **SequencedIdGenerator** uses the provided sequence directly.

### SEQUENCES

It is necessary to provide the "mySequence" instance in the previous example.

``` scala
import net.fwbrasil.activate.sequence.Sequence

object mySequence extends Sequence[Int] {
	protected def _nextValue = ??? // obtain the next value from the database
}
```

You can use the two built in sequence mechanisms:

``` scala
class MyEntityIdGenerator 
		extends SegmentedIdGenerator[MyEntity](LongSequenceEntity(sequenceName = "myEntitySequence", step = 10))

class MyEntityIdGenerator
		extends SegmentedIdGenerator[MyEntity](IntSequenceEntity(sequenceName = "myEntitySequence", step = 10))
```

To use these sequences, it is necessary to create tables for the respective entities:

Table LongSequenceEntity
- id: String
- name: String
- value: Long
- step: Int

Table IntSequenceEntity
- id: String
- name: String
- value: Int
- step: Int

### EXAMPLES

The most commom usage scenario:

``` scala
import myPersistenceContext._

class MyEntity 
		extends EntityWithGeneratedID[Int]

class MyEntityIdGenerator 
		extends SegmentedIdGenerator[MyEntity](IntSequenceEntity(sequenceName = "myEntitySequence", step = 10))
```

A more complex example:

``` scala
class ModelIDSequence(name: String)
		extends SequenceEntity[String](name, 1) {
	def _nextValue = {
		value += step
		s"$name%05d" format value
	}
}

object ModelIDSequence {
	def apply(sequenceName: String, step: Int = 1) = {
		transactional(requiresNew) {
			select[ModelIDSequence].where(_.name :== sequenceName).headOption.getOrElse {
				new ModelIDSequence(sequenceName)
			} 
		}
	} 
}

class ModelSID extends SequencedIdGenerator[Model](ModelIDSequence("m"))
```

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

Activate provides a compressed byte array enconder implementation (ZipEncoder). Example usage:

``` scala
case class CompressedEntityValue(string: String)

class CompressedEntityValueEncoder extends ZipEncoder[CompressedEntityValue] {
    
    protected def write(value: CompressedEntityValue, stream: DataOutputStream) = {
        val bytes = value.string.getBytes
        stream.writeInt(bytes.length)
        stream.writeBytes(value.string)
    }
    protected def read(stream: DataInputStream) = {
    	val size = stream.readInt 
        val array = new Array[Byte](size)
        stream.readFully(array)
        val string = new String(array)
        new CompressedEntityValue(string)
    }
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
@InternalAlias("PersonTable")
class Person(@InternalAlias("personName") var name: String) extends Entity
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
