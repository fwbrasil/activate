import sbt._
import Keys._

object ActivateBuild extends Build {

    /* Core dependencies */
    val javassist = "org.javassist" % "javassist" % "3.18.2-GA"
    val radonStm = "net.fwbrasil" %% "radon-stm" % "1.7"
    val smirror = "net.fwbrasil" %% "smirror" % "0.9"
    val guava = "com.google.guava" % "guava" % "16.0"
    val objenesis = "org.objenesis" % "objenesis" % "2.1"
    val jug = "com.fasterxml.uuid" % "java-uuid-generator" % "3.1.3"
    val reflections = "org.reflections" % "reflections" % "0.9.8" exclude ("javassist", "javassist") exclude ("dom4j", "dom4j")
    val grizzled = "org.clapper" %% "grizzled-slf4j" % "1.0.2"
    val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.0"
    val jodaTime = "joda-time" % "joda-time" % "2.3"
    val jodaConvert = "org.joda" % "joda-convert" % "1.6"
    val blueprintsCore = "com.tinkerpop.blueprints" % "blueprints-core" % "2.4.0"
    val blueprintsNeo4j = "com.tinkerpop.blueprints" % "blueprints-neo4j-graph" % "2.4.0"
    val gremlin = "com.tinkerpop.gremlin" % "gremlin-java" % "2.4.0"
    val xstream = "com.thoughtworks.xstream" % "xstream" % "1.4.6" exclude ("xpp3", "xpp3_min")
    val jettison = "org.codehaus.jettison" % "jettison" % "1.3.4"
    val findBugs = "com.google.code.findbugs" % "jsr305" % "2.0.1"
    val kryo = "com.esotericsoftware.kryo" % "kryo" % "2.23.0"
    val cassandraDriver = "com.datastax.cassandra" % "cassandra-driver-core" % "1.0.5"

    /* Prevayler */
    val prevaylerCore = "org.prevayler" % "prevayler-core" % "2.6"
    val prevaylerFactory = "org.prevayler" % "prevayler-factory" % "2.6"
    val prevaylerXStream = "org.prevayler.extras" % "prevayler-xstream" % "2.6"

    /* 
    Install oracle in your local repo
  */
    val objbd6 = "com.oracle" % "ojdbc6" % "11.2.0"
    val mysql = "mysql" % "mysql-connector-java" % "5.1.28"
    val postgresql = "org.postgresql" % "postgresql" % "9.3-1100-jdbc4"
    val hirakiCP = "com.zaxxer" % "HikariCP" % "1.3.8"
    val h2 = "com.h2database" % "h2" % "1.3.175"
    val derby = "org.apache.derby" % "derby" % "10.10.1.1"
    val hqsqldb = "org.hsqldb" % "hsqldb" % "2.3.1"
    val db2jcc = "com.ibm.db2.jcc" % "db2jcc4" % "10.1"
    val jtds = "net.sourceforge.jtds" % "jtds" % "1.3.1"

    val gfork = "org.gfork" % "gfork" % "0.11"

    /* Mongo */
    val mongoDriver = "org.mongodb" % "mongo-java-driver" % "2.11.4"

    lazy val activate =
        Project(
            id = "activate",
            base = file("."),
            aggregate = Seq(activateCore, activatePrevayler,
                activateJdbc, activateMongo, activateTest, activatePlay,
                activateGraph, activateSprayJson, activateJdbcAsync,
                activateSlick, activateMongoAsync, activatePrevalent,
                activateCassandraAsync, activateLift, activateFinagleMysql),
            settings = commonSettings
        )

    val finagleMysql = "com.twitter" %% "finagle-mysql" % "6.25.0"

    lazy val activateFinagleMysql =
        Project(
            id = "activate-finagle-mysql",
            base = file("activate-finagle-mysql"),
            dependencies = Seq(activateCore, activateJdbc),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(finagleMysql)
            )
        )


    lazy val activateCore =
        Project(
            id = "activate-core",
            base = file("activate-core"),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(javassist, radonStm, objenesis, jug,
                        reflections, grizzled, logbackClassic, jodaTime, jodaConvert,
                        smirror, xstream, jettison, findBugs, kryo, guava)
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
                    Seq(hirakiCP)
            )
        )

    val slick = "com.typesafe.slick" %% "slick" % "2.1.0"
    val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.11.2"

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

    val postgresqlAsync = "com.github.mauricio" %% "postgresql-async" % "0.2.15"
    val mysqlAsync = "com.github.mauricio" %% "mysql-async" % "0.2.15"

    lazy val activateJdbcAsync =
        Project(
            id = "activate-jdbc-async",
            base = file("activate-jdbc-async"),
            dependencies = Seq(activateCore, activateJdbc),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(postgresqlAsync, mysqlAsync)
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

    val reactivemongo = "org.reactivemongo" %% "reactivemongo" % "0.10.5.akka23-SNAPSHOT"

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

    val play = "com.typesafe.play" %% "play" % "2.3.2"
    val playTest = "com.typesafe.play" %% "play-test" % "2.3.2"

    lazy val activatePlay =
        Project(
            id = "activate-play",
            base = file("activate-play"),
            dependencies = Seq(activateCore, activateTest, activateJdbc),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(play, playTest % "provided", specs2 % "provided")
            )
        )

    val lift = "net.liftweb" %% "lift-webkit" % "2.6-RC1"

    lazy val activateLift =
        Project(
            id = "activate-lift",
            base = file("activate-lift"),
            dependencies = Seq(activateCore),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(lift)
            )
        )

    val sprayJson = "io.spray" %% "spray-json" % "1.2.6"

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
        "com.fasterxml.jackson.core" % "jackson-core" % "2.3.1",
        "com.fasterxml.jackson.core" % "jackson-annotations" % "2.3.1",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.1",
        "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.2")

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

    val junit = "junit" % "junit" % "4.11"
    val specs2 = "org.specs2" %% "specs2" % "2.4.2"

    lazy val activateTest =
        Project(id = "activate-test",
            base = file("activate-test"),
            dependencies = Seq(activateCore, activatePrevayler % "test", activateJdbc % "test",
                activateMongo % "test", activateGraph % "test", activateSprayJson % "test", activateJacksonJson % "test", activateJdbcAsync % "test",
                               activateSlick % "test", activateMongoAsync % "test", activatePrevalent % "test", activateCassandraAsync % "test", activateLift % "test",
                               activateFinagleMysql % "test"),
            settings = commonSettings ++ Seq(
                libraryDependencies ++=
                    Seq(junit % "test", specs2 % "test", mysql % "test", objbd6 % "test", postgresql % "test", db2jcc % "test",
                        h2 % "test", derby % "test", hqsqldb % "test", gfork % "test", blueprintsNeo4j % "test", jtds % "test"),
                scalacOptions ++= Seq("-Xcheckinit")
            )
        )

    /* Resolvers */
    val customResolvers = Seq(
        "Maven" at "http://repo1.maven.org/maven2/",
        "Typesafe" at "http://repo.typesafe.com/typesafe/releases",
        "Local Maven Repository" at "file://" + Path.userHome + "/.m2/repository",
        "fwbrasil.net" at "http://fwbrasil.net/maven/",
        "spray" at "http://repo.spray.io/",
        "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
        "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
        "Alfesco" at "https://maven.alfresco.com/nexus/content/groups/public/"
    )

    def commonSettings =
        Defaults.defaultSettings ++ Seq(
            organization := "net.fwbrasil",
            version := "1.7",
            scalaVersion := "2.11.2",
            javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
            publishMavenStyle := true,
            // publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))), 
            // publishTo := Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")),
            publishTo <<= version { v: String =>
                val nexus = "https://oss.sonatype.org/"
                val fwbrasil = "http://fwbrasil.net/maven/"
                if (v.trim.endsWith("SNAPSHOT"))
                    Option(Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as ("maven") withPermissions ("0644"))
                else
                    Some("releases" at nexus + "service/local/staging/deploy/maven2")
            },
            resolvers ++= customResolvers,
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
