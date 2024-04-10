package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.gopas.todolist.Model._
import mongo4cats.circe._
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.{Filter, Index, Update}
import io.circe.generic.auto._
import CirceCodecs._
import com.gopas.todolist.Config.MongoConfig
import mongo4cats.models.client.{ConnectionString, MongoClientSettings}
import mongo4cats.models.collection.IndexOptions
import org.bson.UuidRepresentation

class MongoDbService private (taskCollection: MongoCollection[IO, TaskDetail]) extends DescriptionService {

  // TaskDetail currently consists only of Description, thats why whole detail is optional in DB

  private def taskIdFilter(taskId: Id[Task]): Filter = Filter.eq("taskId", taskId.value.toString)

  def getTaskDetail(taskId: Id[Task]): IO[TaskDetail] =
    taskCollection
      .find(taskIdFilter(taskId))
      .first
      .map(_.getOrElse(TaskDetail(taskId, Option.empty[Description])))

  def insertDescription(taskId: Id[Task], description: Description): IO[Unit] =
    taskCollection
      .insertOne(TaskDetail(taskId, Some(description)))
      .map(_ => ())

  def updateDescription(taskId: Id[Task], description: Description): IO[Unit] =
    for {
      updateResult <- taskCollection.updateOne(taskIdFilter(taskId), Update.set("description", description))
      _ <- if (updateResult.getMatchedCount < 1) insertDescription(taskId, description) else ().pure[IO]
    } yield ()

  def deleteDescription(taskId: Id[Task]): IO[Unit] =
    taskCollection
      .deleteOne(taskIdFilter(taskId))
      .map(_ => ())

}

object MongoDbService {

  def apply(config: MongoConfig): Resource[IO, MongoDbService] =
    MongoClient
      .create[IO](
        MongoClientSettings.builder()
          .applyToConnectionPoolSettings(builder => builder.minSize(config.poolMinSize).maxSize(config.poolMaxSize))
          .applyConnectionString(ConnectionString(config.connectionString))
          .uuidRepresentation(UuidRepresentation.STANDARD)
          .build()
      )
      .evalMap(_.getDatabase(config.database))
      .evalMap(_.getCollectionWithCodec[TaskDetail]("todocol"))
      .evalTap(_.createIndex(Index.text("taskId"), IndexOptions().unique(true)))
      .map(new MongoDbService(_))

}