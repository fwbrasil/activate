package net.fwbrasil.activate.storage.relational.async

import scala.concurrent.ExecutionContext
import scala.concurrent.{Future => ScalaFuture}
import scala.concurrent.{Promise => ScalaPromise}
import scala.util.Failure
import scala.util.Success

import com.twitter.util.{Future => TwitterFuture}
import com.twitter.util.{Promise => TwitterPromise}

object FutureBridge {

    implicit def twitterToScala[T](future: TwitterFuture[T]): ScalaFuture[T] = {
        val promise = ScalaPromise[T]()
        future.onSuccess(promise.success)
        future.onFailure(promise.failure)
        promise.future
    }

    implicit def scalaToTwitter[T](future: ScalaFuture[T])(implicit ctx: ExecutionContext): TwitterFuture[T] = {
        val promise = TwitterPromise[T]()
        future.onComplete {
            case Success(result) =>
                promise.setValue(result)
            case Failure(exception) =>
                promise.setException(exception)
        }
        promise
    }
}