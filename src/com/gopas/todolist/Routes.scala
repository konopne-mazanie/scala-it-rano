package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.gopas.todolist.Model.{AccessToken, IsDone}
import Helpers._
import com.typesafe.scalalogging.StrictLogging
import org.http4s.HttpRoutes
import sttp.model.StatusCode
import sttp.tapir.server.http4s.Http4sServerInterpreter

object Routes extends StrictLogging {

  import Endpoints._

  def apply(service: Service): HttpRoutes[IO] = {

    def securityLogic(token: AccessToken): IO[Either[(StatusCode, String), Unit]] =
      IO(if (token.value == "test") Right(()) else Left(StatusCode.Unauthorized, "invalid token"))

    def handleError[T](op: IO[T]): IO[Either[(StatusCode, String), T]] =
      op
        .map(Right(_))
        .handleErrorWith {
          case throwable =>
            for {
              _ <- logger.errorIO("Unexpected request processing failure", throwable)
              errorResponse = Left(StatusCode.InternalServerError, throwable.getMessage)
            } yield errorResponse
        }

    def handleResult[T](result: IO[T]): IO[Either[(StatusCode, String), (StatusCode, T)]] =
      handleError(result.map(res => (StatusCode.Ok, res)))

    def handleEmptyResult(result: IO[Unit]): IO[Either[(StatusCode, String), StatusCode]] =
      handleError(result.map(_ => StatusCode.Ok))

    val serverEndpoints = List(
      getTaskList
        .serverSecurityLogic(securityLogic)
        .serverLogic(_ => service.getTaskList andThen handleResult),
      createTask
        .serverSecurityLogic(securityLogic)
        .serverLogic(_ => service.createTask andThen handleResult),
      getTask
        .serverSecurityLogic(securityLogic)
        .serverLogic(_ => service.getTaskDetail andThen handleResult),
      deleteTask
        .serverSecurityLogic(securityLogic)
        .serverLogic(_ => service.deleteTask andThen handleEmptyResult),
      editTask
        .serverSecurityLogic(securityLogic)
        .serverLogic(_ => service.editTask.tupled andThen handleEmptyResult),
      setTaskDone
        .serverSecurityLogic(securityLogic)
        .serverLogic(_ => (service.setTaskDone(_, IsDone(true))) andThen handleEmptyResult),
      unsetTaskDone
        .serverSecurityLogic(securityLogic)
        .serverLogic(_ => (service.setTaskDone(_, IsDone(false))) andThen handleEmptyResult)
    )

    Http4sServerInterpreter[IO]().toRoutes(serverEndpoints)
  }

}