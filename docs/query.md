# QUERY #
Activate queries are consistent, even with entities created in the current transaction, so a new entity can be returned in a query. This is achieved by the LiveCache, which implements in memory the behavior of a database.

**Important:**

- Remember to use queries inside transactions.

- Use only attributes accessors inside a query. If you use a method that isn’t an accessor, Activate can produce a wrong result.

Given this entities:

``` scala
trait Person extends Entity {
    var name: String
}
class NaturalPerson(var name: String, var motherName: String) extends Person
class LegalPerson(var name: String, var director: NaturalPerson) extends Person
```
There are some forms of query:


## BYID ##
If you have the entity id, this method is the best way to recover the instance. It has better performance. If the entity is already in memory, Activate can avoid a database trip.

``` scala
byId[Person](personId)
```
The method return is an Option[Person].


## ALL ##
Get all persons:

``` scala
all[Person]
```
Note that the queries can be made about abstract entities (trait and abstract class).


## SELECT WHERE ##
Get all natural persons with the name “John” and mother name “Mary”.

``` scala
select[NaturalPerson] where(_.name :== "John", _.motherName :== "Mary")
```
## OPERATORS ##
Activate query operators are preceded by a “:”.

The available operators are :**||**, :**&&**, :**==**, :**!=**, :**<**, :**>**, :**<=**, :**>=**, **isNull** and **isNotNull**.

You have to use parenthesis to Scala recognize the implicit conversions to Query. Example:

Get all natural persons with the name “John” or mother name “Mary”.

``` scala
select[NaturalPerson] where(person => (person.name :== "John") :|| (person.motherName :== "Mary"))
```
## MATCH OPERATORS ##
Activate supports queries that matches strings by using the operators **like** and **regexp**.

Natural persons that the name start with “John”:

``` scala
select[NaturalPerson] where(_.name like "John*")
```
Use “*” to indicate any sequence of characters and “?” to indicate one any character.

Natural persons with numbers on name:

``` scala
select[NaturalPerson] where(_.name regexp "^.*[0-9].*$")
```
Activate relies on the storage regex engine, so it’s possible that the query result varies according to the current storage. For memory and prevayler storages, java regex is used.


## COMPLETE QUERY FORM ##
The “all” and “select where” queries are a simplified form of the complete query:

``` scala
query {
    (person: Person) => where(person.name :== "John") select (person)
}
```
It is also possible to define an empty where:

``` scala
query {
    (person: Person) => where() select (person)
}
```

## STRING CASE MANIPULATION ##
It is possible to use toUpperCase(string) and toLowerCase(string) as query functions:

``` scala
query {
    (person: Person) => where(toUpperCase(person.name) :== "JOHN") select (person)
}
```

## NESTED PROPERTIES ##
Queries using more than one entity or nested properties:

``` scala
query {
    (company: LegalPerson, director: NaturalPerson) => where(company.director :== director) select (company, director)
}
query {
    (company: LegalPerson) => where(company.director.name :== "John2") select (company)
}
```
NOTE: Queries involving more than one entity or nested properties are not supported by MongoStorage.


## ORDERED QUERY ##
The complete query form supports the order by clause:

``` scala
query {
    (entity: MyEntity) =>
        where(entity.attribute like "A*") select (entity) orderBy (entity.attribute)
}
```
If the entity attribute doesn’t support ordering, a compile error with be thrown. It’s possible have multiple order by criterias and/or define directions:

``` scala
query {
    (entity: MyEntity) =>
        where(entity.attribute1 like "A*") select (entity) orderBy (entity.attribute1 asc, entity.attribute2 desc)
}
```

## LIMITED QUERY ##
It is possible to limit the number of returned rows in a query by adding the “limit” clause after the “orderBy”:

``` scala
query {
    (entity: MyEntity) =>
        where(entity.attribute like "A*") select (entity) orderBy (entity.attribute) limit(10)
}
```
It is possible to define an offset:

``` scala
query {
    (entity: MyEntity) =>
        where(entity.attribute like "A*") select (entity) orderBy (entity.attribute) limit(10) offset(20)
}
```

## PAGINATED QUERY ##
Paginated query example:

``` scala
paginatedQuery {
    (entity: MyEntity) =>
        where(entity.attribute like "A*") select (entity) orderBy (entity.attribute)
}.navigator(100)
```
Note that the query must be ordered to be paginated. It returns a Pagination instance. The “navigator” method call produces a navigable pagination interface (PaginationNavigator) using the parameter as the page size.

Methods and values of **PaginationNavigator**:

- **val numberOfResults: Int**
 
	Total number of lines returned by the query.

- **val numberOfPages: Int**
 
	Number of pages determined by the page size.

- **def hasNext: Boolean**
 
	Indicates if the navigator has a next page.

- **def next: List[S]**
 
	Go to next page and return it.

- **def page(number: Int): List[S]**
 
	Go to the specified page and return it.

- **def currentPage: List[S]**
 
	Return the current page elements.

- **def firstPage: List[S]**

	Go to the first page and return it.

- **def lastPage: List[S]**

	Go to the last page and return it.


## QUERY PARSE CACHE ##
For the complete query form, Activate maintains a parse cache. With this functionality if a query is already parsed before, it is bounded to the current parameters, avoiding to parse again.

This cache doesn’t works with “all” and “select where”. If you have a query that will run too many times, prefer to use the complete query form.


## DYNAMIC QUERIES ##
The default query method uses the query parse cache, so it is not possible to define dynamic queries that changes its structure according to the parameters.

Dynamic queries skip the parse cache, so it is possible to define queries like this example:

``` scala
def someQuery(asc: Boolean) =
    dynamicQuery {
        (e: MyEntity) =>
            where() select (e) orderBy {
                if (asc)
                    e.name asc
                else
                    e.name desc
            }
    }
```
## CACHED QUERIES ##
The cachedQuery is a mechanism to maintain a in-memory cache of the query results. The cache is consistent with new and modified entities by transactions in the same VM. It is possible to enable the optimistic locking with read validation so the cache can be updated for modified entities in other VMs too. For now, the cache can’t be updated automatically for new entities created in other VMs. This limitation should be resolved on the 1.5 version.

The cache just maintain the entities IDs, so there aren’t strong references to the entities.

Example of a cachedQuery:

``` scala
cachedQuery {
    (person: Person) => where(person.name :== "John")
}
```
There isn’t a select clause. Cached queries can be used only to select the entity instances.

The cache is initialized lazily for each set of query input parameters.


## EAGER QUERIES ##
By default, Activate queries are lazy and just load the entity id. When needed, the entity values are loaded from the database.
To force the entity properties loading it is possible to use eager queries. For example:

``` scala
query {
    (person: Person) => where(person.name :== "John") select(e.eager)
}
```
The “eager” keyword can be used only for entities inside the “select” clause.
It is also possible to use it for nested properties:

``` scala
query {
    (person: Person) => where(person.name :== "John") select(e.contact.eager)
}
```
To eager load relations, it is necessary to do explicit joins. For example:

``` scala
query {
    (person: Person, contact: Contact) => where(person.contact :== contact) select(e.eager, contact.eager)
}
```

## ASYNC QUERIES ##
Activate provides async variations of each query form. The query syntax and operators are the same from non-async queries. Examples:

``` scala
asyncById[Person](personId)
asyncSelect[NaturalPerson] where(_.name :== "John", _.motherName :== "Mary")
asyncQuery {
    (person: Person) => where(person.name :== "John") select (person)
}
```
Async queries must be executed inside a asyncTransactionalChain block that provides a TransactionalExecutionContext (the “ctx” parameter):

``` scala
val aFuture: Future[List[Person]] =
    asyncTransactionalChain { implicit ctx =>
        asyncAll[Person]
    }
```
It is possible to compose the future using map operations:

``` scala
val aFuture: Future[Unit] =
    asyncTransactionalChain { implicit ctx =>
        asyncAll[Person].map {
            persons => persons.foreach(_.delete)
        }
    }
```
## ASYNC PAGINATED QUERIES ##
There is a async variation for paginated queries too:

``` scala
val aFuture: Future[AsyncPaginationNavigator[String]] =
    asyncPaginatedQuery {
        (entity: MyEntity) =>
            where(entity.attribute like "A*") select (entity) orderBy (entity.attribute)
    }.navigator(100)
```
Methods and values of **AsyncPaginationNavigator**:

- **val numberOfResults: Int**

	Total number of lines returned by the query.

- **val numberOfPages: Int**

	Number of pages determined by the page size.

- **def hasNext: Boolean**

	Indicates if the navigator has a next page.

- **def next: Future[List[S]]**

	Go to next page and return it.

- **def page(number: Int): Future[List[S]]**

	Go to the specified page and return it.

- **def currentPage: Future[List[S]]**

	Return the current page elements.

- **def firstPage: Future[List[S]]**

	Go to the first page and return it.

- **def lastPage: Future[List[S]]**

	Go to the last page and return it.


## SLICK QUERIES ##
To use slick lifted embedding queries, add the “**activate-slick**” module to the project dependencies.

The slick operations are provided by extending the persistence context from the SlickQueryContext:

``` scala
import net.fwbrasil.activate.slick.SlickQueryContext
 
object myContext extends ActivateContext with SlickQueryContext {
    val storage = ...
}
```
A query example:

``` scala
SlickQuery[MyEntity].filter(_.attribute.col === someValue).execute
```
Activate can avoid the necessity to declare the slick Table objects, but for each usage of an entity property, it is necessary to add the “.col”, so Activate can create the correspondent Slick column definition.

The “execute” call integrates with the persistence context storage to perform the query and return the results.

Refer to the [slick](http://slick.typesafe.com/) documentation to more information on how to construct slick lifted embedding queries.

Another more complex query:

``` scala
(for {
    c <- SlickQuery[Coffee]
    s <- SlickQuery[Supplier].sortBy(_.id.col) if c.supplier.col === s.col
} yield (c.name.col)).sortBy(_.asc).take(2)
```
