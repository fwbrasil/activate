package sbt

import java.io.File
import sbt.Process._

case class AppenginePathFinder(pathFinder: PathFinder)
case class AppengineTestPathFinder(pathFinder: PathFinder)

abstract class AppengineProject(info: ProjectInfo) extends DefaultWebProject(info) {
  override def unmanagedClasspath = super.unmanagedClasspath +++ appengineClasspath
  override def testUnmanagedClasspath = super.testUnmanagedClasspath +++ appengineTestClasspath

  override def webappUnmanaged =
    (temporaryWarPath / "WEB-INF" / "appengine-generated" ***)

  def appengineClasspath: PathFinder = appengineApiJarPath +++ Reflective.reflectiveMappings[AppenginePathFinder](this).values.foldLeft(Path.emptyPathFinder)(_ +++ _.pathFinder)
  def appengineTestClasspath: PathFinder = (appengineLibImplPath * "*.jar") +++ (appengineLibPath / "testing" * "*.jar") +++ Reflective.reflectiveMappings[AppengineTestPathFinder](this).values.foldLeft(Path.emptyPathFinder)(_ +++ _.pathFinder)

  def appengineApiJarName = "appengine-api-1.0-sdk-" + sdkVersion + ".jar"
  def appengineApiLabsJarName = "appengine-api-labs-" + sdkVersion + ".jar"
  def appengineJSR107CacheJarName = "appengine-jsr107cache-" + sdkVersion + ".jar"
  def jsr107CacheJarName = "jsr107cache-1.1.jar"

  def appengineLibPath = appengineSdkPath / "lib"
  def appengineLibUserPath = appengineLibPath / "user"
  def appengineLibImplPath = appengineLibPath / "impl"
  def appengineApiJarPath = appengineLibUserPath / appengineApiJarName
  object AppengineApiLabsJar extends AppenginePathFinder(appengineLibUserPath / appengineApiLabsJarName)
  object Jsr107cacheJars extends AppenginePathFinder(appengineLibUserPath / appengineJSR107CacheJarName +++ appengineLibUserPath / jsr107CacheJarName)

  def appengineToolsJarPath = (appengineLibPath / "appengine-tools-api.jar")

  /* Overrides jar present as of sdk 1.4.3 */
  def appengineOverridePath = appengineSdkPath / "lib" / "override"
  def overridesJarName = "appengine-dev-jdk-overrides.jar"
  def appengineOverridesJarPath = appengineOverridePath / overridesJarName

  def appcfgName = "appcfg" + osBatchSuffix
  def appcfgPath = appengineSdkPath / "bin" / appcfgName

  def isWindows = System.getProperty("os.name").startsWith("Windows")
  def osBatchSuffix = if (isWindows) ".cmd" else ".sh"

  def sdkVersion = {
    val pat = """appengine-api-1.0-sdk-(\d\.\d\.\d(?:\.\d)*)\.jar""".r
    (appengineLibUserPath * "appengine-api-1.0-sdk-*.jar").get.toList match {
      case jar::_ => jar.name match {
        case pat(version) => version
        case _ => error("invalid jar file. " + jar)
      }
      case _ => error("not found appengine api jar.")
    }
  }

  def appengineSdkPath = {
    val sdk = System.getenv("APPENGINE_SDK_HOME")
    if (sdk == null) error("You need to set APPENGINE_SDK_HOME")
    Path.fromFile(new File(sdk))
  }

  lazy val requestLogs = requestLogsAction
  lazy val rollbackWebapp = rollbackWebappAction
  lazy val updateWebapp = updateWebappAction
  lazy val updateIndexes = updateIndexesAction
  lazy val updateCron = updateCronAction
  lazy val updateQueues = updateQueuesAction
  lazy val updateDos = updateDosAction
  lazy val cronInfo = cronInfoAction

  def requestLogsAction = task{ opts => appcfgTask("request_logs", Some("request.log"), opts) } describedAs("Write request logs in Apache common log format.")
  def rollbackWebappAction = task{ opts => appcfgTask("rollback", None, opts) } describedAs("Rollback an in-progress update.")
  def updateWebappAction = task{ opts => appcfgTask("update", None, opts) dependsOn(prepareWebapp) } describedAs("Create or update an app version.")
  def updateIndexesAction = task{ opts => appcfgTask("update_indexes", None, opts) dependsOn(prepareWebapp) } describedAs("Update application indexes.")
  def updateCronAction = task{ opts => appcfgTask("update_cron", None, opts) dependsOn(prepareWebapp) } describedAs("Update application cron jobs.")
  def updateQueuesAction = task{ opts => appcfgTask("update_queues", None, opts) dependsOn(prepareWebapp) } describedAs("Update application task queue definitions.")
  def updateDosAction = task{ opts => appcfgTask("update_dos", None, opts) dependsOn(prepareWebapp) } describedAs("Update application DoS protection configuration.")
  def cronInfoAction = task{ opts => appcfgTask("cron_info", None, opts) } describedAs("Displays times for the next several runs of each cron job.")

  def appcfgTask(action: String, outputFile: Option[String], options: Seq[String]) = interactiveTask {
    val terminal = Some(jline.Terminal.getTerminal).filter(_.isInstanceOf[jline.UnixTerminal]).map(_.asInstanceOf[jline.UnixTerminal])
    val command: ProcessBuilder = <x>
      {appcfgPath.absolutePath} {options.mkString(" ")} {action} {temporaryWarPath.absolutePath} {outputFile.mkString}
    </x>
    log.debug("Executing command " + command)
    terminal.foreach(_.restoreTerminal)
    try {
      val exitValue = command.run(true).exitValue() // don't buffer output
      if(exitValue == 0)
        None
      else
        Some("Nonzero exit value: " + exitValue)
    } finally {
      terminal.foreach(_.initializeTerminal)
    }
  }

  lazy val javaCmd = (Path.fromFile(new java.io.File(System.getProperty("java.home"))) / "bin" / "java").absolutePath

  def appengineAgentPath = appengineLibPath / "agent" / "appengine-agent.jar"

  def devAppserverJvmOptions:Seq[String] = List()
  lazy val devAppserverInstance = new DevAppserverRun
  lazy val devAppserverStart = devAppserverStartAction
  lazy val devAppserverStop = devAppserverStopAction
  def devAppserverStartAction = task{ args => devAppserverStartTask(args) dependsOn(prepareWebapp) }
  def devAppserverStopAction = devAppserverStopTask
  def devAppserverStartTask(args: Seq[String]) = task {devAppserverInstance(args)}
  def devAppserverStopTask = task {
    devAppserverInstance.stop()
    None
  }

  class DevAppserverRun extends Runnable with ExitHook {
    ExitHooks.register(this)
    def name = "dev_appserver-shutdown"
    def runBeforeExiting() { stop() }

    val jvmOptions =
      List("-ea", "-javaagent:"+appengineAgentPath.absolutePath,
           "-cp", appengineToolsJarPath.absolutePath,
           "-Xbootclasspath/p:"+appengineOverridesJarPath) ++ devAppserverJvmOptions

    private var running: Option[Process] = None

    def run() {
      running.foreach(_.exitValue())
      running = None
    }

    def apply(args: Seq[String]): Option[String] = {
      if (running.isDefined)
        Some("An instance of dev_appserver is already running.")
      else {
        val builder: ProcessBuilder =
          Process(javaCmd :: jvmOptions :::
                  "com.google.appengine.tools.development.DevAppServerMain" ::
                  args.toList ::: temporaryWarPath.absolutePath :: Nil,
                  Some(temporaryWarPath.asFile))
        running = Some(builder.run())
        new Thread(this).start()
        None
      }
    }

    def stop() {
      running.foreach(_.destroy)
      running = None
      log.debug("stop")
    }
  }

}

trait DataNucleus extends AppengineProject {
  override def prepareWebappAction = super.prepareWebappAction dependsOn(enhance)

  val appengineORMJarsPath = AppenginePathFinder(appengineLibUserPath / "orm" * "*.jar")
  def appengineORMEnhancerClasspath = (appengineLibPath / "tools" / "orm" * "datanucleus-enhancer-*.jar")  +++ (appengineLibPath / "tools" / "orm" * "asm-*.jar")

  lazy val enhance = enhanceAction
  lazy val enhanceCheck = enhanceCheckAction
  def enhanceAction = enhanceTask(false) dependsOn(compile) describedAs("Executes ORM enhancement.")
  def enhanceCheckAction = enhanceTask(true) dependsOn(compile) describedAs("Just check the classes for enhancement status.")
  def usePersistentApi = "jdo"
  def enhanceTask(checkonly: Boolean) =
    runTask(Some("org.datanucleus.enhancer.DataNucleusEnhancer"),
      appengineToolsJarPath +++ appengineORMEnhancerClasspath +++ compileClasspath ,
      List("-v",
           "-api", usePersistentApi,
           (if(checkonly) "-checkonly" else "")) ++
      mainClasses.get.map(_.absolutePath))
}

trait JRebel extends AppengineProject {
  override def devAppserverJvmOptions =
    if (jrebelPath.isDefined)
      List("-javaagent:" + jrebelPath.get.absolutePath,
           "-noverify") ++ jrebelJvmOptions ++ super.devAppserverJvmOptions
    else
      super.devAppserverJvmOptions

  def jrebelJvmOptions:Seq[String] = List()
  def jrebelPath = {
    val jrebel = System.getenv("JREBEL_JAR_PATH")
    if (jrebel == null) {
      log.error("You need to set JREBEL_JAR_PATH")
      None
    } else
      Some(Path.fromFile(new File(jrebel)))
  }

}
