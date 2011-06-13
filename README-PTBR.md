Activate Persistence Framework

Introdução
==========

O Activate é um novo framework para persistência de objetos em Scala. Ele é um [STM](http://en.wikipedia.org/wiki/Software_transactional_memory "STM") (Software Transacional Memory) durável, com persistência plugável. 
Seu núcleo é o [RadonSTM](https://github.com/fwbrasil/radon-stm "RadonSTM"), que provê um poderoso o mecanismo de controle de transações em memória, análogo às transações dos bancos de dados, para controle de concorrência de memória compartilhada.
A durabilidade das transações (persistência) é plugável, sendo possível utilizar persistência em diferentes paradigmas como relacional (JDBC), prevalência (Prevayler) e não relacional (Cassandra).
Atualmente o suporte para bancos de dados não relacionais está em fase de desenvolvimento.

Benefícios
==========

Os principais benefícios do framework são:

* Transações atômicas, consistentes, isoladas e duráveis. É possível utilizar as entidades sem se preocupar com problemas de concorrência.
* As entidades sempre estão consistentes em memória e na camada de persistência. Por exemplo, ao ocorrer rollback, as entidades em memória não ficam em um estado inconsistente.
* Controle de propagação das transações, incluindo transações aninhadas.
* Persistência transparente. Basta usar as entidades dentro de transações e elas são automaticamente persistidas.
* As entidades são carregadas de forma lazy e inicializadas transparentemente quando necessário.
* As consultas são type-safe e consistentes, inclusive com os objetos criados na transação corrente. Portanto, uma entidade criada na mesma transação pode ser retornada em uma consulta.

Dependencia
===========

Adicione a dependência do Activate ao seu projeto:

[SBT](http://code.google.com/p/simple-build-tool/ "SBT")

	val radonStm = "net.fwbrasil" %% "activate" % "0.0.1"
	val fwbrasil = "fwbrasil.net" at "http://fwbrasil.net/maven/"
	
Adicione esta linha no projeto SBT:

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

Utilização
==========

Inicialmente, deve ser criado o contexto do Activate. O contexto deve ser um singleton, portanto faz sentido declarar como "object":

Prevayler

	object prevaylerContext extends ActivateTestContext {
		def contextName = "prevaylerContext"
		val storage = new PrevaylerMemoryStorage {}
	}

Memória transiente

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
	
É importante que o nome do contexto seja único, porém você pode possuir vários contextos na mesma VM. Em breve o storage poderá ser configurado por xml.

Para utilizar o contexto, importe ele:

	import prevaylerContext._

As entidades devem estender do trait "Entity". Suas propriedades devem ser do tipo "Var", que é a unidade de controle transacional:

	class Pessoa(val nome: Var[String], val idade: Var[Int]) extends Entity

** IMPORTANTE: Cerifique-se de sempre declarar as Vars como finais (val) e utilizar valores imutáveis dentro delas.

Utilize as entidades sempre dentro de transações:

	transacional {
		val pessoa = new Pessoa("Teste", 20)
		val nomePessoaOption = pessoa.nome.get
		val nomePessoa = !pessoa.nome
		pessoa.nome := "Test2"
		pessoa.nome.put(Option("Teste2"))
	}

Existem conversões implícitas dos valores para as Vars. Você pode obter o valor de um atributo usando o método get ou o unário ! e preencher com := ou put.
Atualmente, somente alguns tipos básicos de objetos são suportados. Caso algum tipo não seja suportado, o código não irá compilar porque a conversão implícita não será encontrada.

Não é necessário chamar um método como "store" ou "save" para adicionar a entidade. Apenas a crie, utilize, e ela será persistida.

Consultas:

	val q = query {
		(pessoa: Pessoa) => where(pessoa.nome :== "Teste") select(pessoa)
	}

Os operadores de consulta atualmente disponíveis são :==, :<, :>, :<=, :>=, isNone, isSome, :|| and :&&.

Execute as consulta dentro de transações:

	transacional {
		val result = q.execute
		for(pessoa <- result)
			println(!pessoa.nome)
	}

Existem formas alternativas de consulta:

	transactional {
		val pessoaList1 = all[Pessoa]
		val pessoaList2 = allWhere[Pessoa](_.nome :== "Teste", _.idade :> 10)
	}

É possível utilizar uma lista de critérios no allWhere.

Para apagar uma entidade:

	transactional {
		for(pessoa <- all[Pessoa])
			pessoa.delete
	}

Tipicamente os blocos transacionais são controlados pelo framework. Porém é possível controlar a transação como segue:

	val transaction = new Transaction
	transactional(transaction) {
	    new Pessoa("Teste", 20)
	}
	transaction.commit

Definindo a propagação de transação:

	transactional {
	    val pessoa = new Pessoa("Teste", 20)
	    transactional(mandatory) {
	        pessoa.nome := "Teste2"
	    }
	    println(!pessoa.nome)
	}

Transações aninhadas são um tipo de propagação:

	transactional {
	    val pessoa = new Pessoa("Teste", 20)
	    transactional(nested) {
	        pessoa.nome := "Teste2"
	    }
	    println(!pessoa.nome)
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

* Sempre adicione uma coluna "ID" do tipo VARCHAR2(36) nas tabelas das entidades.
* O nome da tabela é o nome simples da classe da entidade.

Limitações
==========

O Activate não está preparado para ser utilizado em VMs paralelas, uma vez que o controle transacional é em memória e podem surgir inconsitências nos dados na memória das diferentes VMs. Quebrar essa limitação é o foco atual do desenvolvimento.

Licença
=======

O código é licenciado como LGPL.


