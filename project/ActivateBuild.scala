import sbt._
import Keys._

object ActivateBuild extends Build {
  
	/* Core dependencies */
  	val javassist = "org.javassist" % "javassist" % "3.15.0-GA"
	val radonStm = "net.fwbrasil" %% "radon-stm" % "0.3-SNAPSHOT"
	val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
	val objenesis = "org.objenesis" % "objenesis" % "1.2"
	val jug = "org.safehaus.jug" % "jug" % "2.0.0"
	val reflections = "org.reflections" % "reflections" % "0.9.5-RC2"  intransitive() 
	val googleCollections = "com.google.collections" % "google-collections" % "1.0"
	val dom4j = "dom4j" % "dom4j" % "1.6"
	val gson = "com.google.code.gson" % "gson" % "1.4"
	val servlet = "javax.servlet" % "servlet-api" % "2.5"
	val grizzled = "org.clapper" %% "grizzled-slf4j" % "0.6.6"
	val logbackClassic = "ch.qos.logback" % "logback-classic" % "0.9.29"
	val jodaTime = "joda-time" % "joda-time" % "2.0"
	val jodaConvert = "org.joda" % "joda-convert" % "1.1"
	
	/* Prevayler */
	val prevayler = "org.prevayler" % "prevayler" % "2.3"
	
	/* Cassandra */
	val cassandra = "org.apache.cassandra" % "cassandra-thrift" % "1.0.6"
	
	/* Tests */
	val junit = "junit" % "junit" % "4.4" % "test"
	val specs2 = "org.specs2" %% "specs2" % "1.7" % "test"
	val scalaz = "org.specs2" %% "specs2-scalaz-core" % "6.0.RC2" % "test"
	val objbd6 = "com.oracle" % "ojdbc6" % "11.1.0.7.0"
	val mysql = "mysql" % "mysql-connector-java" % "5.1.16"
	def specs2Framework = new TestFramework("org.specs2.runner.SpecsFramework")
  	
  	/* Resolvers */
  	val customResolvers = Seq(
  	    "snapshots" at "http://scala-tools.org/repo-snapshots",
  	    "releases" at "http://scala-tools.org/repo-releases",
  	    "Maven" at "http://repo1.maven.org/maven2/",
  	    "www.mvnsearch.org" at "http://www.mvnsearch.org/maven2/",
  	    "fwbrasil.net" at "http://fwbrasil.net/maven/",
  	    "reflections" at "http://reflections.googlecode.com/svn/repo"
  	)

    lazy val activate = 
    	Project(
    		id = "activate",
    		base = file("."),
    		aggregate = Seq(activateCore, activatePrevayler, 
    		    activateJdbc, activateCassandra, activateTests),
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
    		dependencies = Seq(activateCore)
    	)
                           
    lazy val activateCassandra = 
    	Project(
    	    id = "activate-cassandra",
    	    base = file("activate-cassandra"),
    	    dependencies = Seq(activateCore),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(cassandra)
		    )
    	)             

    lazy val activateTests = 
		Project(id = "activate-tests",
			base = file("activate-tests"),
			dependencies = Seq(activateCore, activatePrevayler, activateJdbc, 
			    activateCassandra),
			settings = commonSettings ++ Seq(
		      libraryDependencies ++= 
		    	  Seq(junit, specs2, scalaz, objbd6, mysql)
		    )
		)
    		  
    def commonSettings = 
    	Defaults.defaultSettings ++ Seq(
    		organization := "net.fwbrasil",
    		version := "0.5-SNAPSHOT",
    	    testFrameworks ++= Seq(specs2Framework),
    	    publishTo := Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")),
    	    organization := "net.fwbrasil",
    	    scalaVersion := "2.9.1",
    	    resolvers ++= customResolvers
    	)
}