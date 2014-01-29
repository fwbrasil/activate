package net.fwbrasil.activate.play

import play.api.Play
import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable.Specification
import net.fwbrasil.activate.test.ActivateTest
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.test._
import org.specs2.execute.AsResult
import org.specs2.specification.Example
import org.openqa.selenium.WebDriver

trait ActivatePlayTest extends ActivateTest {
    self: Specification =>

    def fakeApp = FakeApplication(
        additionalPlugins = Seq("net.fwbrasil.activate.play.ActivatePlayPlugin"))
        
    def webDriverClass: Class[_ <: WebDriver] = HTMLUNIT

    implicit class DSL(thing: String) {

        def inActivate[R](f: => R)(
            implicit asResult: AsResult[R], context: ActivateContext): Example =
            inActivate(defaultStrategy)(f)

        def inActivate[R](strategy: ActivateTestStrategy)(f: => R)(
            implicit asResult: AsResult[R], context: ActivateContext): Example =
            self.inExample(thing).in {
                running(fakeApp)(strategy.runTest(f))
            }

        def inBrowserWithActivate[R](f: TestBrowser => R)(
            implicit asResult: AsResult[R], context: ActivateContext): Example =
            inBrowserWithActivate(defaultStrategy)(f)

        def inBrowserWithActivate[R](strategy: ActivateTestStrategy)(f: TestBrowser => R)(
            implicit asResult: AsResult[R], context: ActivateContext): Example =
            self.inExample(thing).in {
                running(TestServer(testServerPort), webDriverClass) { browser =>
                    strategy.runTest(f(browser))
                }
            }
    }
}