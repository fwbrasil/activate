import sbt._
import Keys._
import com.github.siasia._
import WebPlugin._
import PluginKeys._

object ActivateBuild extends Build {
  	
	/* Core dependencies */
  	val javassist = "org.javassist" % "javassist" % "3.16.1-GA"
	val radonStm = "net.fwbrasil" %% "radon-stm" % "0.9-SNAPSHOT"
	val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
	val objenesis = "org.objenesis" % "objenesis" % "1.2"
	val jug = "org.safehaus.jug" % "jug" % "2.0.0" classifier "lgpl"
	val reflections = "org.reflections" % "reflections" % "0.9.5"  intransitive() 
	val googleCollections = "com.google.collections" % "google-collections" % "1.0"
	val dom4j = "dom4j" % "dom4j" % "1.6"
	val gson = "com.google.code.gson" % "gson" % "1.4"
	val servlet = "javax.servlet" % "servlet-api" % "2.5"
	val grizzled = "org.clapper" % "grizzled-slf4j_2.9.1" % "0.6.6"
	val logbackClassic = "ch.qos.logback" % "logback-classic" % "0.9.29"
	val jodaTime = "joda-time" % "joda-time" % "2.0"
	val jodaConvert = "org.joda" % "joda-convert" % "1.1"
	val scalap = "org.scala-lang" % "scalap" % "2.9.1"
	val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.9.1"
	
	/* Prevayler */
	val prevayler = "org.prevayler" % "prevayler" % "2.3"
	
	/* Cassandra */
	val cassandra = "org.apache.cassandra" % "cassandra-thrift" % "1.0.6"
	
	/* Tests */
	val junit = "junit" % "junit" % "4.4" % "test"
	val specs2 = "org.specs2" % "specs2_2.9.2-RC2" % "1.9" % "test"
	/* 
		Install oracle in your local repo
	*/
	val objbd6 = "com.oracle" % "ojdbc6" % "11.2.0"
	val mysql = "mysql" % "mysql-connector-java" % "5.1.16"
	val postgresql = "postgresql" % "postgresql" % "9.1-901.jdbc4"
	def specs2Framework = new TestFramework("org.specs2.runner.SpecsFramework")
  	
	/* Mongo */
	val mongoDriver = "org.mongodb" % "mongo-java-driver" % "2.7.2"
  	
  	/* Resolvers */
  	val customResolvers = Seq(
  	    "snapshots" at "http://scala-tools.org/repo-snapshots",
  	    "releases" at "http://scala-tools.org/repo-releases",
  	    "Maven" at "http://repo1.maven.org/maven2/",
  	    "www.mvnsearch.org" at "http://www.mvnsearch.org/maven2/",
  	    "fwbrasil.net" at "http://fwbrasil.net/maven/",
  	    "reflections" at "http://reflections.googlecode.com/svn/repo",
  	    "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/",
  	    "Local Maven Repository" at "file://"+Path.userHome+"/.m2/repository"
  	)

    lazy val activate = 
    	Project(
    		id = "activate",
    		base = file("."),
    		aggregate = Seq(activateCore, activatePrevayler, 
    		    activateJdbc, activateMongo, 
    		    activateTests),
    		settings = commonSettings
    	)

    lazy val activateCore = 
		Project(
			id = "activate-core",
			base = file("activate-core"),
    		settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(javassist, radonStm, commonsCollections, objenesis, jug,
		    	      reflections, googleCollections, dom4j, gson, servlet,
		    	      grizzled, logbackClassic, jodaTime, jodaConvert)
		    )
		)

    lazy val activatePrevayler = 
		Project(
			id = "activate-prevayler",
			base = file("activate-prevayler"),
			dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(prevayler)
		    )
		)
                           
    lazy val activateJdbc = 
    	Project(
    	    id = "activate-jdbc",
    		base = file("activate-jdbc"),
    		dependencies = Seq(activateCore),
    		settings = commonSettings
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
    	
    lazy val activateTests = 
		Project(id = "activate-tests",
			base = file("activate-tests"),
			dependencies = Seq(activateCore, activatePrevayler, activateJdbc, 
			    activateMongo),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(junit, specs2, mysql, objbd6, postgresql)
		    )
		)
    	
    def commonSettings = 
    	Defaults.defaultSettings ++ Seq(
    		organization := "net.fwbrasil",
    		version := "0.9-SNAPSHOT",
    		scalaVersion := "2.9.2",
    	    testFrameworks ++= Seq(specs2Framework),
    	    publishMavenStyle := true,
    	    // publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))), 
    	    publishTo := Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")),
    	    resolvers ++= customResolvers,
    	    compileOrder := CompileOrder.JavaThenScala
    	)
}
