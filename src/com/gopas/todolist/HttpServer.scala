package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.gopas.todolist.Config.APIConfig
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object HttpServer {

  def apply(routesFn: WebSocketBuilder2[IO] => HttpRoutes[IO], config: APIConfig): Resource[IO, Server] = {
    val swaggerRoutes =
      Http4sServerInterpreter[IO]().toRoutes(
        SwaggerInterpreter().fromEndpoints[IO](Endpoints.endpoints, "TODO List", "1.0.0")
      )

    BlazeServerBuilder[IO]
      .bindHttp(config.port, config.host)
      .withHttpWebSocketApp(wsb => Router("/" -> (routesFn(wsb) <+> swaggerRoutes)).orNotFound)
      .resource
  }

}