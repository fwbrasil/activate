# SPRAY JSON #
The [spray-json](https://github.com/spray/spray-json) integration is a easy way to manipulate entities from/to json. To use it, import the “**activate-spray-json**” module. If the spray dependency is not found, please add the spray repository to your sbt configuration: [http://repo.spray.io/](http://repo.spray.io/).

There are two ways to use the integration:

1. Extend you activate context from **SprayJsonActivateContext**:

``` scala
import net.fwbrasil.activate.json.spray.SprayJsonContext
 
object myActivateContext extends ActivateContext with SprayJsonContext {
    val storage = ...
}
```
2. Create a separate json context:

``` scala
import net.fwbrasil.activate.json.spray.SprayJsonContext
 
object myJsonContext extends SprayJsonContext {
    val context = myActivateContext
}
 
import myJsonContext._
```
## USAGE ##
Entity to json:

``` scala
val entityJson: JsObject = myEntity.toJson
val entityJsonString: String = myEntity.toJsonString
```
By default, references to other entities will print only the referenced id. It is possible to define a depth to print the referenced entities content:

``` scala
val entityJson: JsObject = myEntity.toJson(depth = 1)
val entityJson: JsObject = myEntity.toJson(fullDepth)
val entityJsonString: String = myEntity.toJsonString(depth = 1)
val entityJsonString: String = myEntity.toJsonString(fullDepth)
```
Update entity from json:

``` scala
def updateEntityFromJson[E <: Entity : Manifest](json: String, id: String): E
def updateEntityFromJson[E <: Entity : Manifest](json: JsObject, id: String): E
def updateEntityFromJson[E <: Entity : Manifest](json: String, entity: E): E
def updateEntityFromJson[E <: Entity : Manifest](json: JsObject, entity: E): E
def updateEntityFromJson[E <: Entity : Manifest](json: String): E
def updateEntityFromJson[E <: Entity : Manifest](json: JsObject): E
```
If it is not provided an id or an entity instance, the id field is read from the json content.

Create entity from json:

``` scala
def createEntityFromJson[E <: Entity : Manifest](json: String): E
def createEntityFromJson[E <: Entity : Manifest](json: JsObject): E
```
There are methods to create or update entities based on a json:

``` scala
def createOrUpdateEntityFromJson[E <: Entity : Manifest](json: String): E
def createOrUpdateEntityFromJson[E <: Entity : Manifest](json: JsObject): E
```
If the json has an id, the entity is updated. Otherwise, a new entity is created.