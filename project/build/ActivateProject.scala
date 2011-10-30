import sbt._

class ActivateProject(info: ProjectInfo) extends ParentProject(info) {

  /* subprojects */

  lazy val core = project("activate-core", "ActivateCoreProject", new ActivateCoreProject(_))
  lazy val prevayler = project("activate-prevayler", "ActivatePrevaylerProject", new ActivatePrevaylerProject(_), core)
  lazy val jdbc = project("activate-jdbc", "ActivateJdbcProject", new ActivateJdbcProject(_), core)
  lazy val appengine = project("activate-appengine", "ActivateAppengineProject", new ActivateAppengineProject(_), core)
  lazy val tests = project("activate-tests", "ActivateTestsProject", new ActivateTestsProject(_), prevayler, jdbc)

  val snapshots = "snapshots" at "http://scala-tools.org/repo-snapshots"
  val releases = "releases" at "http://scala-tools.org/repo-releases"
  val maven = "Maven" at "http://repo1.maven.org/maven2/"
  val mvnsearch = "www.mvnsearch.org" at "http://www.mvnsearch.org/maven2/"
  val fwbrasil = "fwbrasil.net" at "http://reflections.googlecode.com/svn/repo"
  val reflections_repo = "reflections-repo" at "http://fwbrasil.net/maven/"


  trait Commom {
    lazy val publishTo = Resolver.ssh("fwbrasil.net repo", "fwbrasil.net", 8080) as("maven") withPermissions("0644")
  }

  class ActivateCoreProject(info: ProjectInfo) extends DefaultProject(info) with Commom {

	override def name = "activate-core"

	val radonStm = "net.fwbrasil" %% "radon-stm" % "0.2"
	val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
	val objenesis = "org.objenesis" % "objenesis" % "1.2"
	val scalap = "org.scala-lang" % "scalap" % "2.9.0"
	val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.9.0"
	val jug = "org.safehaus.jug" % "jug" % "2.0.0" classifier "lgpl"
	val reflections = "org.reflections" % "reflections" % "0.9.5-RC2"
	val grizzled = "org.clapper" %% "grizzled-slf4j" % "0.5"
	val logbackClassic = "ch.qos.logback" % "logback-classic" % "0.9.29"

	override def filterScalaJars = false
	override def managedStyle = ManagedStyle.Maven
}

class ActivatePrevaylerProject(info: ProjectInfo) extends DefaultProject(info) with Commom {

	override def name = "activate-prevayler"

	val activate = "net.fwbrasil" %% "activate-core" % projectVersion.value.toString

	val prevayler = "org.prevayler" % "prevayler" % "2.3"

	override def filterScalaJars = false
	override def managedStyle = ManagedStyle.Maven
}

class ActivateJdbcProject(info: ProjectInfo) extends DefaultProject(info) with Commom {

	override def name = "activate-jdbc"

	val activate = "net.fwbrasil" %% "activate-core" % projectVersion.value.toString

	override def filterScalaJars = false
	override def managedStyle = ManagedStyle.Maven
}

class ActivateAppengineProject(info: ProjectInfo) extends DefaultProject(info) with Commom {

        override def name = "activate-appengine"

        val activate = "net.fwbrasil" %% "activate-core" % projectVersion.value.toString

        override def filterScalaJars = false
        override def managedStyle = ManagedStyle.Maven
}

  class ActivateTestsProject(info: ProjectInfo) extends DefaultProject(info) with Commom {


	override def name = "activate-tests"

  	def specs2Framework = new TestFramework("org.specs2.runner.SpecsFramework")
	override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)

	val activate = "net.fwbrasil" %% "activate-core" % projectVersion.value.toString

	val activatePrevayler = "net.fwbrasil" %% "activate-prevayler" % projectVersion.value.toString
	val activateJDBC = "net.fwbrasil" %% "activate-jdbc" % projectVersion.value.toString

	val objbd6 = "com.oracle" % "ojdbc6" % "11.1.0.7.0"
	val mysql = "mysql" % "mysql-connector-java" % "5.1.16"

	override def filterScalaJars = false
	override def managedStyle = ManagedStyle.Maven

}
}
