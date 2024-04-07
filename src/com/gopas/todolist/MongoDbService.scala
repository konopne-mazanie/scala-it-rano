package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.gopas.todolist.Model._
import mongo4cats.circe._
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.Filter
import io.circe.generic.auto._
import CirceCodecs._
import com.gopas.todolist.Config.MongoConfig
import mongo4cats.models.client.{ConnectionString, MongoClientSettings}

class MongoDbService private (taskCollection: MongoCollection[IO, TaskDetail]) extends DescriptionService {

  //def getTestString(): IO[Option[Task]] = taskCollection.find(Filter.empty).first

  def getTaskDetail(taskId: Id[Task]): IO[TaskDetail] = taskCollection.find(Filter.empty).first.map(_.get)

  def insertDescription(taskId: Id[Task], description: Description): IO[Unit] = ().pure[IO]

  def updateDescription(taskId: Id[Task], description: Description): IO[Unit] = ().pure[IO]

  def deleteDescription(taskId: Id[Task]): IO[Unit] = ().pure[IO]

}

object MongoDbService {

  def apply(config: MongoConfig): Resource[IO, MongoDbService] =
    MongoClient
      .create[IO](
        MongoClientSettings.builder()
          .applyToConnectionPoolSettings(builder => builder.minSize(config.poolMinSize).maxSize(config.poolMaxSize))
          .applyConnectionString(ConnectionString(config.connectionString))
          .build()
      )
      .evalMap(_.getDatabase(config.database))
      .evalMap(_.getCollectionWithCodec[TaskDetail]("todocol"))
      .map(new MongoDbService(_))

}