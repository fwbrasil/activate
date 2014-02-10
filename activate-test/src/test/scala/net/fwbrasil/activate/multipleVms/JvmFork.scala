package net.fwbrasil.activate.multipleVms

import org.gfork.Fork
import java.net.URLClassLoader
import java.io.File

case class FunctionTaskReturn(value: Any)
case class FunctionTask[R](f: () => R) {
    def run: Serializable =
        FunctionTaskReturn(f())
}

case class ForkTask[R](fork: Fork[FunctionTask[R], Nothing]) {
    def execute = {
        fork.execute
        this
    }
    def joinAndGetResult = {
        join
        fork.getReturnValue.asInstanceOf[FunctionTaskReturn].value.asInstanceOf[R]
    }
    def join = {
        fork.waitFor
        println(fork.getStdOut)
        System.err.println(fork.getStdErr)
        Option(fork.getException).map(throw _)
    }
}

object JvmFork {

    Fork.setJvmOptionsForAll("-server")
    
    val classpath = getClass.getClassLoader.asInstanceOf[URLClassLoader].getURLs.map(_.getFile.toString).mkString(File.pathSeparator)
    
    System.setProperty("java.class.path", classpath)

    def fork[R: Manifest](ms: Int = 100, mx: Int = 1024, others: List[String] = List())(f: => R): ForkTask[R] = {
        val fork = new Fork(FunctionTask(() => f), classOf[FunctionTask[_]].getMethod("run"))
        fork.addJvmOption("-Xmx" + mx + "M")
        fork.addJvmOption("-Xms" + ms + "M")
        others.map(fork.addJvmOption)
        ForkTask[R](fork)
    }

    def forkAndExpect[R: Manifest](ms: Int = 100, mx: Int = 1024, others: List[String] = List())(f: => R): R =
        fork[R](ms, mx, others)(f).execute.joinAndGetResult
}