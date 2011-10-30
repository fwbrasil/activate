import sbt._

class ActivateAppEngineProject(info: ProjectInfo) extends AppengineProject(info) {
    val servletapi = "javax.servlet" % "servlet-api" % "2.5"

    val appengine = "com.google" % "appengine-api-1.0-sdk" %  "1.5.1"

    val scalaToolsSnapshots = "Scala Tools Repository" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
    val sonatypeNexusSnapshots = "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    val sonatypeNexusReleases = "Sonatype Nexus Releases" at "https://oss.sonatype.org/content/repositories/releases"
    val fuseSourceSnapshots = "FuseSource Snapshot Repository" at "http://repo.fusesource.com/nexus/content/repositories/snapshots"


}
