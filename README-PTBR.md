Activate Persistence Framework

Introdução
==========

O Activate é um framework para persistência de objetos em Scala. Ele é um [STM](http://en.wikipedia.org/wiki/Software_transactional_memory "STM") (Software Transacional Memory) durável, com persistência plugável. 
Seu núcleo é o [RadonSTM](https://github.com/fwbrasil/radon-stm "RadonSTM"), que provê um poderoso o mecanismo de controle de transações em memória, análogo às transações dos bancos de dados, para controle de concorrência otimista.
A durabilidade das transações (persistência) é plugável, sendo possível utilizar persistência em diferentes paradigmas como relacional (JDBC), prevalência (Prevayler) e não relacional (MongoDB).

Benefícios
==========

Os principais benefícios do framework são:

* Transações atômicas, consistentes, isoladas e duráveis. É possível utilizar as entidades sem se preocupar com problemas de concorrência.
* As entidades sempre estão consistentes em memória e na camada de persistência. Por exemplo, ao ocorrer rollback, as entidades em memória não ficam em um estado inconsistente.
* Controle de propagação das transações, incluindo transações aninhadas.
* Persistência transparente. Basta usar as entidades dentro de transações e elas são automaticamente persistidas.
* As entidades são carregadas de forma lazy e inicializadas automaticamente quando necessário.
* As consultas são type-safe e consistentes, inclusive com os objetos criados na transação corrente. Portanto, uma entidade criada na mesma transação pode ser retornada em uma consulta.

Artefatos
===========

Adicione as dependências necessárias do Activate ao seu projeto:

[xSBT](https://github.com/harrah/xsbt/ "xSBT")

	resolvers += "fwbrasil.net" at "http://fwbrasil.net/maven/"
	libraryDependencies += "net.fwbrasil" %% "activate-core" % "0.6"
	libraryDependencies += "net.fwbrasil" %% "activate-prevayler" % "0.6"
	libraryDependencies += "net.fwbrasil" %% "activate-jdbc" % "0.6"
	libraryDependencies += "net.fwbrasil" %% "activate-mongo" % "0.6"

Download direto

	

Utilização
==========

Inicialmente, deve ser criado o contexto do Activate. O contexto deve ser um singleton, portanto faz sentido declarar como "object":

Prevayler

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.prevayler.PrevaylerMemoryStorage

	object prevaylerContext extends ActivateContext {
		def contextName = "prevaylerContext"
		val storage = new PrevaylerMemoryStorage
	}

Memória transiente

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.memory.MemoryStorage

	object memoryContext extends ActivateContext {
		def contextName = "memoryContext"
		val storage = new MemoryStorage
	}

Oracle

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
	import net.fwbrasil.activate.storage.relational.oracleDialect

	object oracleContext extends ActivateContext {
		def contextName = "oracleContext"
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
			val user = "USER"
			val password = "PASS"
			val url = "jdbc:oracle:thin:@localhost:1521:oracle"
			val dialect = oracleDialect
		}
	}

Mysql

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
	import net.fwbrasil.activate.storage.relational.mySqlDialect

	object mysqlContext extends ActivateContext {
		def contextName = "mysqlContext"
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "com.mysql.jdbc.Driver"
			val user = "root"
			val password = "root"
			val url = "jdbc:mysql://127.0.0.1/test"
			val dialect = mySqlDialect
		}
	}

MongoDB

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.mongo.MongoStorage

	object mongoContext extends ActivateContext {
		val storage = new MongoStorage {
			override val host = "localhost"
			override val port = 27017
			override val db = "dbName"
		}
	}

É importante que o nome do contexto seja único, porém você pode possuir vários contextos na mesma VM.

Para utilizar o contexto, importe ele:

	import prevaylerContext._

Desta forma, as classes necessárias como Entity e Query estarão no escopo. As entidades devem estender do trait "Entity":

	abstract class Pessoa(var nome: String) extends Entity
	class PessoaFisica(nome: String, var nomeMae: String) extends Pessoa(nome)
	class PessoaJuridica(nome: String, var diretor: PessoaFisica) extends Pessoa(nome)

É possível declarar as propriedades como val ou var, caso estas sejam imutáveis ou não.

Utilize as entidades sempre dentro de transações:

	transactional {
		val pessoa = new PessoaFisica("Fulano", "Maria")
		pessoa.nome = "Fulano2"
		println(pessoa.nome)
	}

Não é necessário chamar um método como "store" ou "save" para adicionar a entidade. Apenas a crie, utilize, e ela será persistida.

Consultas:

	val q = query {
		(pessoa: Pessoa) => where(pessoa.nome :== "Teste") select(pessoa)
	}

Os operadores de consulta disponíveis são :==, :<, :>, :<=, :>=, isNone, isSome, :|| and :&&. Observe que as queries podem ser feitas sobre super classes (incluindo trait e abstract class).

Execute as consultas dentro de transações:

	transactional {
		val result = q.execute
		for (pessoa <- result)
			println(pessoa.nome)
	}

Existem formas alternativas de consulta. Com o allWhere possível utilizar uma lista de critérios.

	transactional {
		val pessoaList1 = all[Pessoa]
		val pessoaList2 = allWhere[PessoaFisica](_.nome :== "Teste", _.nomeMae :== "Mae")
	}

Queries utilizando mais de uma entidade ou com propriedades aninhadas:

	val q2 = query {
		(empresa: PessoaJuridica, diretor: PessoaFisica) => where(empresa.diretor :== diretor) select (empresa, diretor)
	}
	val q3 = query {
		(empresa: PessoaJuridica) => where(empresa.diretor.nome :== "Silva") select(empresa)
	}

Obs.: Queries que envolvem mais de uma entidade não são suportadas pelo MongoStorage.

Para apagar uma entidade:

	transactional {
		for(pessoa <- all[Pessoa])
			pessoa.delete
	}

Tipicamente os blocos transacionais são controlados pelo framework. Porém é possível controlar a transação como segue:

	val transaction = new Transaction
	transactional(transaction) {
		new PessoaFisica("Teste", "Mae")
	}
	transaction.commit

Definindo a propagação da transação:

	transactional {
		val pessoa = new PessoaFisica("Teste", "Mae")
		transactional(mandatory) {
			pessoa.nome = "Teste2"
		}
		println(pessoa.nome)
	}

Transações aninhadas são um tipo de propagação:

	transactional {
		val pessoa = new PessoaFisica("Teste", "Mae")
		transactional(nested) {
			pessoa.nome = "Teste2"
		}
		println(pessoa.nome)
	}

As propagações disponíveis são baseadas nas do EJB:

* required
* requiresNew
* mandatory
* notSupported
* supports
* never
* nested

Banco de dados
==============

Este é o mapeamento entre os tipos dos atributos das entidades e os tipos dos bancos de dados:

Tipo         | Mysql       | Oracle
-------------|-------------|-----------------
ID           | VARCHAR(50) | VARCHAR2(50)
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
Entity       | VARCHAR(50) | VARCHAR2(50)
Enumeration  | VARCHAR(20) | VARCHAR2(20)

* Sempre adicione uma coluna "ID" nas tabelas das entidades.
* O nome da tabela é o nome da classe da entidade.
* O tipo AbstractInstant (JodaTime) segue o mesmo mapemanento do tipo Date.

Licença
=======

O código é licenciado como LGPL.


