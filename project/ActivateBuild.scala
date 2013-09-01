import sbt._
import Keys._

object ActivateBuild extends Build {
  	
	/* Core dependencies */
  	val javassist = "org.javassist" % "javassist" % "3.17.1-GA"
	val radonStm = "net.fwbrasil" %% "radon-stm" % "1.4-SNAPSHOT"
	val smirror = "net.fwbrasil" %% "smirror" % "0.5"
	val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
	val objenesis = "org.objenesis" % "objenesis" % "1.2"
	val jug = "com.fasterxml.uuid" % "java-uuid-generator" % "3.1.3"
	val reflections = "org.reflections" % "reflections" % "0.9.8" exclude("javassist", "javassist") exclude("dom4j", "dom4j")
	val googleCollections = "com.google.collections" % "google-collections" % "1.0"
	val grizzled = "org.clapper" %% "grizzled-slf4j" % "1.0.1"
	val logbackClassic = "ch.qos.logback" % "logback-classic" % "0.9.29"
	val jodaTime = "joda-time" % "joda-time" % "2.0"
	val jodaConvert = "org.joda" % "joda-convert" % "1.1"
	val play = "play" %% "play" % "2.1.0"
	val blueprintsCore = "com.tinkerpop.blueprints" % "blueprints-core" % "2.2.0"
	val blueprintsNeo4j = "com.tinkerpop.blueprints" % "blueprints-neo4j-graph" % "2.2.0"
	val gremlin = "com.tinkerpop.gremlin" % "gremlin-java" % "2.2.0"
	val xstream = "com.thoughtworks.xstream" % "xstream" % "1.4.3" exclude("xpp3", "xpp3_min")
	val jettison = "org.codehaus.jettison" % "jettison" % "1.3.2"
	val scalaActors = "org.scala-lang" % "scala-actors" % "2.10.0"
	val findBugs = "com.google.code.findbugs" % "jsr305" % "2.0.1"
	val kryo = "com.esotericsoftware.kryo" % "kryo" % "2.21"
	val cassandraDriver = "com.datastax.cassandra" % "cassandra-driver-core" % "1.0.2"
	
	/* Prevayler */
	val prevaylerCore = "org.prevayler" % "prevayler-core" % "2.6"
	val prevaylerFactory = "org.prevayler" % "prevayler-factory" % "2.6"
	val prevaylerXStream = "org.prevayler.extras" % "prevayler-xstream" % "2.6"
	
	/* Tests */
	val junit = "junit" % "junit" % "4.4" % "test"
	val specs2 = "org.specs2" %% "specs2" % "1.13" % "test"
	/* 
		Install oracle in your local repo
	*/
	val objbd6 = "com.oracle" % "ojdbc6" % "11.2.0"
	val mysql = "mysql" % "mysql-connector-java" % "5.1.16"
	val postgresql = "org.postgresql" % "postgresql" % "9.2-1003-jdbc4"
	val boneCP = "com.jolbox" % "bonecp" % "0.7.1.RELEASE"
	val h2 = "com.h2database" % "h2" % "1.3.168"
	val derby = "org.apache.derby" % "derby" % "10.9.1.0"
	val hqsqldb = "org.hsqldb" % "hsqldb" % "2.2.8"
	val db2jcc = "com.ibm.db2" % "db2jcc4" % "10.0.1"

	val gfork = "org.gfork" % "gfork" % "0.11"
  	
	/* Mongo */
	val mongoDriver = "org.mongodb" % "mongo-java-driver" % "2.10.0"

    lazy val activate = 
    	Project(
    		id = "activate",
    		base = file("."),
    		aggregate = Seq(activateCore, activatePrevayler, 
    		    activateJdbc, activateMongo, activateTests, activatePlay,
    		    activateGraph, activateSprayJson, activateJdbcAsync,
    		    activateSlick, activateMongoAsync, activatePrevalent,
    		    activateCassandraAsync),
    		settings = commonSettings
    	)

    lazy val activateCore = 
		Project(
			id = "activate-core",
			base = file("activate-core"),
    		settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(javassist, radonStm, commonsCollections, objenesis, jug,
		    	      reflections, grizzled, logbackClassic, jodaTime, jodaConvert,
		    	      smirror, xstream, scalaActors, jettison, findBugs, kryo)
		    )
		)

    lazy val activatePrevayler = 
		Project(
			id = "activate-prevayler",
			base = file("activate-prevayler"),
			dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(prevaylerCore, prevaylerFactory, prevaylerXStream)
		    )
		)

	lazy val activatePrevalent = 
		Project(
			id = "activate-prevalent",
			base = file("activate-prevalent"),
			dependencies = Seq(activateCore),
			settings = commonSettings
		)

	lazy val activateCassandraAsync = 
		Project(
			id = "activate-cassandra-async",
			base = file("activate-cassandra-async"),
			dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(cassandraDriver)
		    )
		)
                           
    lazy val activateJdbc = 
    	Project(
    	    id = "activate-jdbc",
    		base = file("activate-jdbc"),
    		dependencies = Seq(activateCore),
    		settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(boneCP)
		    )
    	)

    val slick = "com.typesafe.slick" % "slick_2.10" % "2.0.0-M2" withSources
    val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.10.1"

    lazy val activateSlick = 
    	Project(
    	    id = "activate-slick",
    		base = file("activate-slick"),
    		dependencies = Seq(activateCore, activateJdbc),
    		settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(scalaCompiler, slick)
		    )
    	)

    val postgresqlAsync = "com.github.mauricio" %% "postgresql-async" % "0.2.3"

    lazy val activateJdbcAsync =
    	Project(
    	    id = "activate-jdbc-async",
    		base = file("activate-jdbc-async"),
    		dependencies = Seq(activateCore, activateJdbc),
    		settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(postgresqlAsync)
		    )
    	)

    lazy val activateMongo = 
    	Project(
    	    id = "activate-mongo",
    	    base = file("activate-mongo"),
    	    dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(mongoDriver)
		    )
    	)

    val reactivemongo = "org.reactivemongo" %% "reactivemongo" % "0.9"

    lazy val activateMongoAsync =
    	Project(
    	    id = "activate-mongo-async",
    		base = file("activate-mongo-async"),
    		dependencies = Seq(activateCore, activateMongo),
    		settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(reactivemongo)
		    )
    	)

    lazy val activateGraph = 
    	Project(
    	    id = "activate-graph",
    	    base = file("activate-graph"),
    	    dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(blueprintsCore, gremlin)
		    )
    	)
    
	lazy val activatePlay = 
    	Project(
    	    id = "activate-play",
    	    base = file("activate-play"),
    	    dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(play)
		    )
    	)

    val sprayJson = "io.spray" %%  "spray-json" % "1.2.4"

    lazy val activateSprayJson = 
    	Project(
    	    id = "activate-spray-json",
    	    base = file("activate-spray-json"),
    	    dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(sprayJson)
		    )
    	)

  val jackson = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % "2.2.2",
    "com.fasterxml.jackson.core" % "jackson-annotations" % "2.2.2",
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.2.2",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.2.2")

  lazy val activateJacksonJson =
    Project(
      id = "activate-jackson",
      base = file("activate-jackson"),
      dependencies = Seq(activateCore, activateJdbc),
      settings = commonSettings ++ Seq(
        libraryDependencies ++=
          Seq(postgresql, scalaCompiler) ++ jackson
      )
    )

    val json4sNative = "org.json4s" %% "json4s-native" % "3.2.3"

    lazy val activateTests = 
		Project(id = "activate-tests",
			base = file("activate-tests"),
			dependencies = Seq(activateCore, activatePrevayler, activateJdbc, 
			    activateMongo, activateGraph, activateSprayJson, activateJacksonJson, activateJdbcAsync,
			    activateSlick, activateMongoAsync, activatePrevalent, activateCassandraAsync),
			settings = commonSettings ++ Seq(
		     	libraryDependencies ++= 
		    	  Seq(junit, specs2, mysql, objbd6, postgresql, db2jcc,
		    	  	h2, derby, hqsqldb, gfork, blueprintsNeo4j),
		    	 scalacOptions ++= Seq("-Xcheckinit")
		    )
		)

  	/* Resolvers */
  	val customResolvers = Seq(
  	    "Maven" at "http://repo1.maven.org/maven2/",
  	    "Typesafe" at "http://repo.typesafe.com/typesafe/releases",
  	    "Local Maven Repository" at "file://"+Path.userHome+"/.m2/repository",
  	    "fwbrasil.net" at "http://fwbrasil.net/maven/",
  	    "spray" at "http://repo.spray.io/"
  	)

    	
    def commonSettings = 
    	Defaults.defaultSettings ++ Seq(
    		organization := "net.fwbrasil",
    		version := "1.4-SNAPSHOT",
    		scalaVersion := "2.10.1",
    		javacOptions ++= Seq("-source", "1.5", "-target", "1.5"),
    	    publishMavenStyle := true,
    	    // publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))), 
    	    // publishTo := Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")),
    	    publishTo <<= version { v: String =>
				  val nexus = "https://oss.sonatype.org/"
				  val fwbrasil = "http://fwbrasil.net/maven/"
				  if (v.trim.endsWith("SNAPSHOT")) 
				    Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644"))
				  else                             
				    Some("releases" at nexus + "service/local/staging/deploy/maven2")
				},
		    resolvers ++= customResolvers,
			credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials"),
			publishMavenStyle := true,
			publishArtifact in Test := false,
			pomIncludeRepository := { x => false },
			pomExtra := (
			  <url>http://github.com/fwbrasil/activate/</url>
			  <licenses>
			    <license>
			      <name>LGPL</name>
			      <url>https://github.com/fwbrasil/activate/blob/master/LICENSE-LGPL</url>
			      <distribution>repo</distribution>
			    </license>
			  </licenses>
			  <scm>
			    <url>git@github.com:fwbrasil/activate.git</url>
			    <connection>scm:git:git@github.com:fwbrasil/activate.git</connection>
			  </scm>
			  <developers>
			    <developer>
			      <id>fwbrasil</id>
			      <name>Flavio W. Brasil</name>
			      <url>http://fwbrasil.net</url>
			    </developer>
			  </developers>
			),
    	    compileOrder := CompileOrder.JavaThenScala,
    	    parallelExecution in Test := false
    	)
}
