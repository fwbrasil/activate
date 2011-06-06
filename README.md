Introduction
============

Activate is a object persistence framework in Scala. The main features are:

 * Use of RadonSTM to control entities atributes, resulting in a durable STM with optimistc concurrency control
 * The storage backend is pluggable. For now there is support for transient memory, prevayler and jdbc (tested with mysql and oracle). There is a initial implementation for key-value storages, like Apache Cassandra.
 * Lazy loading and transparent activation
 * Type-safe queries
 
Getting Started
===============

Declare a ActivateContext instance:

 * Prevayler

	object prevaylerContext extends ActivateTestContext {
		val storage = new PrevaylerMemoryStorage {
			override lazy val name = "test/PrevalenceBase/testPrevaylerMemoryStorage" + (new java.util.Date).getTime
		}
	}

 * Transient memory

	object memoryContext extends ActivateTestContext {
		val storage = new MemoryStorage {}
	}

 * Oracle

	object oracleContext extends ActivateTestContext {
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
			val user = "USER"
			val password = "PASS"
			val url = "jdbc:oracle:thin:@localhost:1521:oracle"
			val dialect = oracleDialect
			val serializator = javaSerializator
		}
	}

 * Mysql

	object mysqlContext extends ActivateTestContext {
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
	
Extend "Entity" trait and declare atributes as Vars:

	class Person(val name: Var[String], val age: Var[Int]) extends Entity

# IMPORTANT: 
 * Make sure to use immutable values inside Vars
 * Actually, the framework supports only Vars declareds in entity constructor parameters.

Use entities always inside transacion:

	transacional {
		val person = new Person("Test", 20)
		val personNameOption = person.name.get
		val personName = !person.name
		person.name := "Test2"
		person.name.put("Test3")
	}

There are implicit conversions from values to Vars. You can get atribute value by using get ou the unary ! and set attribute using := or put.
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
	*	required
	*	requiresNew
	*	mandatory
	*	notSupported
	*	supports
	*	never
	*	nested


License
=======

All code in this repository is licensed under the GPL version 3, or any later version at your choice. See the LICENSE-GPL file for more details.