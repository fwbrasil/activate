Introduction
============

Activate is an ORM (Object Relational Mapping), since it can be used with relational databases. 
But it also can be used with Prevayler (Object Prevalence Mapping?) and will can be used with No-SQL databases (Object No-SQL Mapping?). 
For now, the best denomination that I've found is "Pluggable Object Persistence". It persists objects, using pluggable storage systems.

Activate also is a durable [STM](http://en.wikipedia.org/wiki/Software_transactional_memory "STM") (Software Transactional Memory). His core is a STM implementation called [RadonSTM](https://github.com/fwbrasil/radon-stm "RadonSTM").  STM gives to the framework:
- A powerful mechanism to handle with transactions in memory, without needing to use transactional control from the storage (witch is absent in some No-SQL databases);
- Atomic, isolated and consistent transactions with optimistic read and write collision detection in concurrent transactions, so you can use the entities without worrying about concurrency, commit and rollback problems;
- In memory transaction propagation control, with nested transactions;

The persistence of the objects is transparent, so just to use the entities inside transactions and the persistence will be achieved. Entities are lazy loaded and transparent activated (initialized) when it's necessary.
Queries are type-safe and consistent with the running transaction, so entities created during the transaction are "queriable" to.
 
Dependency
==========

SBT

	val radonStm = "net.fwbrasil" %% "activate" % "0.0.1"
	val fwbrasil = "fwbrasil.net" at "http://fwbrasil.net/maven/"
	
Add this line to sbt project:

	override def filterScalaJars = false

******************

Maven

	<dependency>
    	<groupId>net.fwbrasil</groupId>
	    <artifactId>activate</artifactId>
    	<version>0.0.1</version>
	</dependency>
	
	<repository>
		<id>fwbrasil</id>
		<url>http://fwbrasil.net/maven/</url>
    </repository>
 
Getting Started
===============

Declare an ActivateContext instance:

Prevayler

	object prevaylerContext extends ActivateTestContext {
		def contextName = "prevaylerContext"
		val storage = new PrevaylerMemoryStorage {}
	}

Transient memory

	object memoryContext extends ActivateTestContext {
		def contextName = "memoryContext"
		val storage = new MemoryStorage {}
	}

Oracle

	object oracleContext extends ActivateTestContext {
		def contextName = "oracleContext"
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
			val user = "USER"
			val password = "PASS"
			val url = "jdbc:oracle:thin:@localhost:1521:oracle"
			val dialect = oracleDialect
			val serializator = javaSerializator
		}
	}

Mysql

	object mysqlContext extends ActivateTestContext {
		def contextName = "mysqlContext"
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "com.mysql.jdbc.Driver"
			val user = "root"
			val password = "root"
			val url = "jdbc:mysql://127.0.0.1/teste"
			val dialect = mySqlDialect
			val serializator = javaSerializator
		}
	}

Import from context:

	import prevaylerContext._
	
Extend "Entity" trait and declare attributes as Vars:

	class Person(val name: Var[String], val age: Var[Int]) extends Entity

********************************************************
IMPORTANT:

 * Make sure to use immutable values inside Vars
 * The framework supports only Vars declareds in entity constructor parameters.
********************************************************

Use entities always inside transaction:

	transacional {
		val person = new Person("Test", 20)
		val personNameOption = person.name.get
		val personName = !person.name
		person.name := "Test2"
		person.name.put(Option("Test3"))
	}

There are implicit conversions from values to Vars. You can get attribute value by using get or the unary ! and set attribute using := or put.
It's not necessary to call a method like store to add the entity, just create and the entity will be persisted.

Create queries:

	val q = query {
		(person: Person) => where(person.name :== "Test") select(person)
	}

Available operators are :==, :<, :>, :<=, :>=, isNone, isSome, :|| and :&&.

Execute queries inside transaction:

	transacional {
		val result = q.execute
		for(person <- result)
			println(!person.name)
	}

There are alternative forms of query:

	transactional {
		val personList1 = all[Person]
		val personList2 = allWhere[Person](_.name :== "Test", _.age :> 10)
	}

You can use a list of criterias in allWhere.

Delete

	transactional {
		for(person <- all[Person])
			person.delete
	}

Typically transactional blocks are controlled by the framework. However, it's possible to control a transaction as follows:

	val transaction = new Transaction
	transactional(transaction) {
	    val person = new Person("Test", 20)
	}
	transaction.commit

You can define a transaction propagation:

	transactional {
	    val person = new Person("Test", 20)
	    transactional(mandatory) {
	        person.name := "Test2"
	    }
	    println(!person.name)
	}

Nested transactions are a type of propagation:

	transactional {
	    val person = new Person("Test", 20)
	    transactional(nested) {
	        person.name := "Test2"
	    }
	    println(!person.name)
	}

The available propagations are based on EJB propagations:

* required
* requiresNew
* mandatory
* notSupported
* supports
* never
* nested

Database types
==============

This is the mapping from Activate attributes and database types:

Attribute     | Mysql       | Oracle
-------------|-------------|-------------
Int          | INTEGER     | INTEGER
Boolean      | BOOLEAN     | NUMBER(1)
Char         | CHAR        | CHAR
String       | VARCHAR     | VARCHAR2
Float        | DOUBLE      | FLOAT
Double       | DOUBLE      | DOUBLE PRECISION
BigDecimal   | DECIMAL     | NUMBER
Date         | LONG        | TIMESTAMP
Calendar     | LONG        | TIMESTAMP
Array[Byte]  | BLOB        | BLOB
Entity       | VARCHAR(36) | VARCHAR2(36)

* Always add a column "ID" of type VARCHAR2(36) to your table entities.
* The name of the table is the name of entity class.


License
=======

All code in this repository is licensed under the LGPL. See the LICENSE-LGPL file for more details.