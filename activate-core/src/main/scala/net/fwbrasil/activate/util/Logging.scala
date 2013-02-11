package net.fwbrasil.activate.util

import grizzled.slf4j.{ Logging => GrizzledLogging }

trait Logging extends GrizzledLogging {

    protected def logTrace[A](id: => String)(f: => A): A =
        logLevel(id, (s: String) => trace(s))(f)

    protected def logDebug[A](id: => String)(f: => A): A =
        logLevel(id, (s: String) => debug(s))(f)

    protected def logInfo[A](id: => String)(f: => A): A =
        logLevel(id, (s: String) => info(s))(f)

    protected def logWarn[A](id: => String)(f: => A): A =
        logLevel(id, (s: String) => warn(s))(f)

    protected def logError[A](id: => String)(f: => A): A =
        logLevel(id, (s: String) => error(s))(f)

    private[this] def logLevel[A](id: => String, level: (String) => Unit)(f: => A): A = {
        level(id)
        f
    }

}