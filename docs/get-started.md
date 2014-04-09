# Introduction

Activate is a framework to persist objects in Scala. It is a [STM](http://en.wikipedia.org/wiki/Software_transactional_memory) (Software Transactional Memory) distributed and durable with pluggable persistence. Its core is the [RadonSTM](https://github.com/fwbrasil/radon-stm), which provides a powerful mechanism for controlling transactions in memory, analogous to the transactions of databases, to do optimistic concurrency control. The durability of transactions is pluggable and can use persistence in different paradigms such as relational (JDBC), prevalence (Prevayler) and non-relational (MongoDB). The framework also has support for polyglot persistence using distributed transactions.

## Sample code

``` scala
package com.example.foo

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.StorageFactory 

object persistenceContext extends ActivateContext {
  val storage = StorageFactory.fromSystemProperties("myStorage")
}

inport persistenceContext._

class Person(var name: String) extends Entity

class CreatePersonTableMigration extends Migration {
  def timestamp = Z012111616051
  def up = {
    table[Person]
      .createTable(
        _.column[String]("name"))
  }
}

object simpleMain extends App {

  transnactional {
    new Person("John")
  }

  val john = transactional {
    select[Person].where(_.name :== "John").head
  }

  transactional {
    john.name = "John Doe"
  }

  transactional {
    all[Person].foreach(_.delete)
  }
}
```

# Benefits

**The main benefits of the framework are**:

- Atomic, consistent, isolated and durable transactions. You can use entities without worrying about concurrency issues.
- Easy and transparent polyglot persistence.
- Entities are always consistent in memory and in the persistence layer. For example, if rollback occurs, entities in memory stay consistent.
- Transaction propagation control, including nested transactions.
- Transaction execution is a non-blocking operation.
- Entities are lazy loaded and initialized automatically when needed.
- Queries are type-safe and consistent, even with objects created in the current transaction.
- The available memory is used efficiently, minimizing the conversation with the storage and maximizing performance.

# How to get Activate

## Repositories URLs to browse artifacts

- http://fwbrasil.net/maven/net/fwbrasil/
- http://repo1.maven.org/maven2/net/fwbrasil/

## Repositories roots

- http://fwbrasil.net/maven
- http://repo1.maven.org/maven2

Group id: **net.fwbrasil**

Artifacts ids:

- **activate-core**
- **activate-play**
- **activate-lift**
- **activate-jdbc**
- **activate-jdbc-async**
- **activate-mongo**
- **activate-mongo-async**
- **activate-prevayler**
- **activate-prevalent**
- **activate-slick**
- **activate-spray-json**

Last version: **1.4.4**

Available scala versions: **2.10**

# Activate example project

A easy way to start with Activate is to use the example project.

Download [activate example project](https://github.com/fwbrasil/activate-example/zipball/v1.4.4).

Install the Simple Build Tool (SBT) following the [instructions](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html).

Modify project/ActivateExampleBuild.scala and com/example/foo/ActivateExampleContext.scala to determine the storage. Memory storage is the default value.

Call sbt inside the activate-example folder and create the eclipse project files:


> $ sbt
> 
> \> eclipse


Now you can import into eclipse. It is necessary that the scala plugin is installed [http://scala-ide.org/](http://scala-ide.org/).

If you want to change the storage, simply change the two classes mentioned above, open the console and rebuild the eclipse project with the same command (“eclipse”).
