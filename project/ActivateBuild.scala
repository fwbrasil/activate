import sbt._
import Keys._

object ActivateBuild extends Build {
  	
	/* Core dependencies */
  	val javassist = "org.javassist" % "javassist" % "3.16.1-GA" withSources
	val radonStm = "net.fwbrasil" %% "radon-stm" % "1.2-SNAPSHOT"
	val sreflection = "net.fwbrasil" %% "sreflection" % "0.1"
	val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
	val objenesis = "org.objenesis" % "objenesis" % "1.2"
	val jug = "com.fasterxml.uuid" % "java-uuid-generator" % "3.1.3"
	val reflections = "org.reflections" % "reflections" % "0.9.8" 
	val googleCollections = "com.google.collections" % "google-collections" % "1.0"
	val grizzled = "org.clapper" % "grizzled-slf4j_2.9.1" % "0.6.6"
	val logbackClassic = "ch.qos.logback" % "logback-classic" % "0.9.29"
	val jodaTime = "joda-time" % "joda-time" % "2.0"
	val jodaConvert = "org.joda" % "joda-convert" % "1.1"
	val play = "play" %% "play" % "2.0.2"
	val blueprintsCore = "com.tinkerpop.blueprints" % "blueprints-core" % "2.1.0"
	val blueprintsNeo4j = "com.tinkerpop.blueprints" % "blueprints-neo4j-graph" % "2.1.0"
	val xstream = "com.thoughtworks.xstream" % "xstream" % "1.4.3"
	val jettison = "org.codehaus.jettison" % "jettison" % "1.3.2"
	
	/* Prevayler */
	val prevaylerCore = "org.prevayler" % "prevayler-core" % "2.5"
	val prevaylerFactory = "org.prevayler" % "prevayler-factory" % "2.5"
	val prevaylerXStream = "org.prevayler.extras" % "prevayler-xstream" % "2.5"
	
	/* Tests */
	val junit = "junit" % "junit" % "4.4" % "test"
	val specs2 = "org.specs2" %% "specs2" % "1.12.1" % "test"
	/* 
		Install oracle in your local repo
	*/
	val objbd6 = "com.oracle" % "ojdbc6" % "11.2.0"
	val mysql = "mysql" % "mysql-connector-java" % "5.1.16"
	val postgresql = "postgresql" % "postgresql" % "9.1-901.jdbc4"
	val c3po = "com.mchange" % "c3p0" % "0.9.2-pre4"
	val h2 = "com.h2database" % "h2" % "1.3.168"
	val derby = "org.apache.derby" % "derby" % "10.9.1.0"
	val hqsqldb = "org.hsqldb" % "hsqldb" % "2.2.8"

	val gfork = "org.gfork" % "gfork" % "0.11"
  	
	/* Mongo */
	val mongoDriver = "org.mongodb" % "mongo-java-driver" % "2.10.0"
  	
  	/* Resolvers */
  	val customResolvers = Seq(
  	    "Maven" at "http://repo1.maven.org/maven2/",
  	    "Typesafe" at "http://repo.typesafe.com/typesafe/releases",
  	    "Local Maven Repository" at "file://"+Path.userHome+"/.m2/repository",
  	    "fwbrasil.net" at "http://fwbrasil.net/maven/"
  	)

    lazy val activate = 
    	Project(
    		id = "activate",
    		base = file("."),
    		aggregate = Seq(activateCore, activatePrevayler, 
    		    activateJdbc, activateMongo, activateTests, activatePlay,
    		    activateGraph),
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
		    	      sreflection, xstream, jettison)
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
                           
    lazy val activateJdbc = 
    	Project(
    	    id = "activate-jdbc",
    		base = file("activate-jdbc"),
    		dependencies = Seq(activateCore),
    		settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(c3po)
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

    lazy val activateGraph = 
    	Project(
    	    id = "activate-graph",
    	    base = file("activate-graph"),
    	    dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(blueprintsCore)
		    )
    	)
    
	lazy val activatePlay = 
    	Project(
    	    id = "activate-play",
    	    base = file("activate-play"),
    	    dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(play),
		    crossScalaVersions := Seq("2.9.1")
		    )
    	)

    lazy val activateTests = 
		Project(id = "activate-tests",
			base = file("activate-tests"),
			dependencies = Seq(activateCore, activatePrevayler, activateJdbc, 
			    activateMongo, activateGraph),
			settings = commonSettings ++ Seq(
		     	libraryDependencies ++= 
		    	  Seq(junit, specs2, mysql, objbd6, postgresql, 
		    	  	h2, derby, hqsqldb, gfork, blueprintsNeo4j),
		    	 scalacOptions ++= Seq("-Xcheckinit")
		    )
		)
    	
    def commonSettings = 
    	Defaults.defaultSettings ++ Seq(
    		organization := "net.fwbrasil",
    		version := "1.2-RC1",
    		scalaVersion := "2.9.1",
    		crossScalaVersions := Seq("2.9.1", "2.9.2"),
    		javacOptions ++= Seq("-source", "1.5", "-target", "1.5"),
    	    publishMavenStyle := true,
    	    // publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))), 
    	    publishTo := Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")),
    	    resolvers ++= customResolvers,
    	    compileOrder := CompileOrder.JavaThenScala,
    	    parallelExecution in Test := false
    	)
}
