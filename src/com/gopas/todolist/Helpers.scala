package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.Logger

object Helpers {

  implicit class IOLogger(logger: Logger) {

    def infoIO(msg: String): IO[Unit] = IO.blocking(logger.info(msg)).start.map(_ => ())

    def warnIO(msg: String, throwableOpt: Option[Throwable] = None): IO[Unit] = {
      val warnFn: () => Unit =
        throwableOpt match {
          case Some(throwable) => () => logger.warn(msg, throwable)
          case None => () => logger.warn(msg)
        }
      IO.blocking(warnFn).start.map(_ => ())
    }

    def errorIO(msg: String, throwable: Throwable): IO[Unit] = IO.blocking(logger.error(msg, throwable)).start.map(_ => ())

  }

}