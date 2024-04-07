package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import Helpers._
import org.http4s.server.Server

object Main extends IOApp.Simple with StrictLogging {

  val serverResource: Resource[IO, Server] =
    for {
      config <- Resource.eval(Config())
      mongoDbService <- MongoDbService(config.mongo)
      pgDbService <- PgDbService(config.postgres, mongoDbService)
      service = new Service(pgDbService, mongoDbService)
      routes = Routes(service)
      server <- HttpServer(routes, config.api)
    } yield server

  val run =
    for {
      _ <- logger.infoIO("Starting server...")
      _ <-
        serverResource
          .useForever
          .handleErrorWith(logger.errorIO("Failed to start server", _))
    } yield ()

}
