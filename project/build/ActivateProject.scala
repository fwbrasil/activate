import sbt._

class ActivateProject(info: ProjectInfo) extends DefaultProject(info) {

	val junit = "junit" % "junit" % "4.4"
	val specs2 = "org.specs2" %% "specs2" % "1.3"
	val scalaz = "org.specs2" %% "specs2-scalaz-core" % "6.0.RC2"
	val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
	val objenesis = "org.objenesis" % "objenesis" % "1.2"
	val scalap = "org.scala-lang" % "scalap" % "2.9.0"
	val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.9.0"
	val cassandra = "org.apache.cassandra" % "cassandra-thrift" % "0.8.0"
	val jug = "org.safehaus.jug" % "jug" % "2.0.0" classifier "lgpl"
	val objbd6 = "com.oracle" % "ojdbc6" % "11.1.0.7.0"
	val mysql = "mysql" % "mysql-connector-java" % "5.1.16"
	val radonStm = "net.fwbrasil" %% "radon-stm" % "0.2"
	val prevayler = "org.prevayler" % "prevayler" % "2.3"
	val reflections = "org.reflections" % "reflections" % "0.9.5-RC2"
	val grizzled = "org.clapper" %% "grizzled-slf4j" % "0.5"
	val logbackClassic = "ch.qos.logback" % "logback-classic" % "0.9.29"

	def specs2Framework = new TestFramework("org.specs2.runner.SpecsFramework")
	override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)

	val snapshots = "snapshots" at "http://scala-tools.org/repo-snapshots"
	val releases = "releases" at "http://scala-tools.org/repo-releases"
	val maven = "Maven" at "http://repo1.maven.org/maven2/"
	val mvnsearch = "www.mvnsearch.org" at "http://www.mvnsearch.org/maven2/"
	
	val fwbrasil = "fwbrasil.net" at "http://reflections.googlecode.com/svn/repo"
	val reflections_repo = "reflections-repo" at "http://fwbrasil.net/maven/"
	
	override def filterScalaJars = false
	
	override def managedStyle = ManagedStyle.Maven
    lazy val publishTo = Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")

}
