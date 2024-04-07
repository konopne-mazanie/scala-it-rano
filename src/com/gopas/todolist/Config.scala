package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.gopas.todolist.Config._
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint

case class Config(
                 api: APIConfig,
                 mongo: MongoConfig,
                 postgres: doobie.hikari.Config
                 )

object Config {

  case class APIConfig(host: String, port: Int)

  case class MongoConfig(
                          connectionString: String,
                          database: String,
                          poolMinSize: Int,
                          poolMaxSize: Int
                        )

  def apply(): IO[Config] = {
    implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

    IO.blocking(ConfigSource.default.loadOrThrow[Config])
  }

}