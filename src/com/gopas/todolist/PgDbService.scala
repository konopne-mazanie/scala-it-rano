package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import doobie.util.log.{ExecFailure, LogEvent, ProcessingFailure, Success}
import doobie.hikari.HikariTransactor
import doobie._
import doobie.implicits._
import doobie.util.fragments._
import doobie.postgres._
import doobie.postgres.implicits._
import Helpers._
import cats.data.NonEmptyList
import com.gopas.todolist.Model._
import com.gopas.todolist.DoobieCodecs._

class PgDbService private (transactor: HikariTransactor[IO], descriptionService: DescriptionService)
  extends StrictLogging {

  private def transact[T](connIO: ConnectionIO[T]): IO[T] = connIO.transact(transactor)

  private def createTags(tags: NonEmptyList[Tag]): ConnectionIO[NonEmptyList[Id[Tag]]] =
    Update[Tag]("INSERT INTO tag(name) VALUES(?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name")
      .updateManyWithGeneratedKeys[Id[Tag]]("id")(tags)
      .compile
      .toList
      .map(NonEmptyList.fromListUnsafe)

  private def pairTagsWTask(taskId: Id[Task], tagIds: NonEmptyList[Id[Tag]]): ConnectionIO[Unit] =
    Update[Id[Tag]](s"INSERT INTO task_tag(task_id, tag_id) VALUES('${taskId.value.toString}'::UUID, ?) ON CONFLICT DO NOTHING")
      .updateMany(tagIds).map(_ => ())

  private def unpairTagsWTask(taskId: Id[Task], tagIds: NonEmptyList[Id[Tag]]): ConnectionIO[Unit] = {
    val deleteFilter = whereAnd(parentheses(fr"task_id = $taskId"), in(fr"tag_id", tagIds))
    fr"DELETE FROM task_tag $deleteFilter".update.run.map(_ => ())
  }

  private def deleteUnpairedTags(): ConnectionIO[Unit] =
    fr"DELETE FROM tag WHERE id NOT IN (SELECT DISTINCT tag_id FROM task_tag)".update.run.map(_ => ())

  private def updateTaskTags(taskId: Id[Task], newTags: Set[Tag]): ConnectionIO[Unit] =
    for {
      insertTagsMap <- newTags.map(_ -> true).toMap.pure[ConnectionIO]
      splitTags <-
        fr"SELECT tag_id, name FROM task_tag INNER JOIN tag ON (tag_id = tag.id) WHERE task_id = $taskId"
          .query[(Id[Tag], Tag)]
          .streamWithChunkSize(20)
          .fold((insertTagsMap, Set.empty[Id[Tag]])) { case ((insertTagsMap, deleteTagIds), (id, tag)) =>
            if (newTags.contains(tag))
              (insertTagsMap + (tag -> false), deleteTagIds)
            else
              (insertTagsMap, deleteTagIds + id)
          }
          .compile
          .last
          .map(_.get)
      (insertTagsMap, deleteTagIds) = splitTags
      _ <-
        NonEmptyList.fromList(deleteTagIds.toList)
          .map(unpairTagsWTask(taskId, _).flatMap(_ => deleteUnpairedTags()))
          .getOrElse(().pure[ConnectionIO])
      _ <-
        NonEmptyList.fromList(insertTagsMap.filter(_._2).keys.toList)
          .map(createTags(_).flatMap(pairTagsWTask(taskId, _)))
          .getOrElse(().pure[ConnectionIO])
    } yield ()

  def getTasks(taskFilter: TaskFilter): IO[TaskList] = transact {
    for {
      tagsQry <-
        fr"""
        |SELECT ARRAY_AGG(tag.name) AS tags, task_id
        |FROM task_tag INNER JOIN tag ON (tag_id = tag.id)
        |GROUP BY task_id
        """.stripMargin.pure[ConnectionIO]
      from = fr"FROM task LEFT JOIN ($tagsQry) tags_qry ON (task.id = task_id)"
      filter =
        whereAndOpt(
          taskFilter.nameFilter.map(name => parentheses(fr"LOWER(name) LIKE ${s"%${name.value.toLowerCase}%"}")),
          taskFilter.tagFilter.map(tag => parentheses(fr"$tag=ANY(tags)")).reduceOption(or(_, _)).map(parentheses),
          taskFilter.isDoneFilter.map(done => parentheses(fr"is_done = $done")),
          taskFilter.deadlineFilter.map(deadline => parentheses(and(
            parentheses(fr"deadline >= ${deadline.from}"),
            parentheses(fr"deadline <= ${deadline.to}")
          ))),
          Some(parentheses(and(
            parentheses(fr"priority >= ${taskFilter.priorityFilter.from}"),
            parentheses(fr"priority <= ${taskFilter.priorityFilter.to}")
          )))
        )
      count <- fr"SELECT COUNT(1) $from $filter".query[Int].unique
      tasks <-
        fr"""
        |SELECT id, name, tags, is_done, deadline, priority
        |$from
        |$filter
        |LIMIT ${taskFilter.paging.count} OFFSET ${taskFilter.paging.from}
        """.stripMargin
          .query[Task]
          .to[List]
    } yield TaskList(tasks, count)
  }

  def deleteTask(taskId: Id[Task]): IO[Unit] =
    WeakAsync.liftK[IO, ConnectionIO].use { liftIO =>
      transact {
        for {
          _ <- fr"DELETE FROM task_tag WHERE task_id = $taskId".update.run
          _ <- deleteUnpairedTags()
          _ <- fr"DELETE FROM task WHERE id = $taskId".update.run
          _ <- liftIO(descriptionService.deleteDescription(taskId))
        } yield ()
      }
    }

  def createTask(createTaskPld: CreateTaskPld): IO[Id[Task]] =
    WeakAsync.liftK[IO, ConnectionIO].use { liftIO =>
      transact {
        for {
          taskId <-
            fr"""
            |INSERT INTO task(name, deadline, priority, is_done)
            |VALUES(${createTaskPld.name}, ${createTaskPld.deadline}, ${createTaskPld.priority}, FALSE)
            """.stripMargin
              .update
              .withUniqueGeneratedKeys[Id[Task]]("id")
          _ <-
            NonEmptyList.fromList(createTaskPld.tags.toList)
              .map(createTags(_).flatMap(pairTagsWTask(taskId, _)))
              .getOrElse(().pure[ConnectionIO])
          _ <-
            createTaskPld.description
              .map(description => liftIO(descriptionService.insertDescription(taskId, description)))
              .getOrElse(().pure[ConnectionIO])
        } yield taskId
      }
    }

  def setTaskDone(taskId: Id[Task], isDone: IsDone): IO[Unit] = transact {
    fr"UPDATE task SET is_done = $isDone WHERE id = $taskId".update.run.map(_ => ())
  }

  def editTask(taskId: Id[Task], editTaskPld: EditTaskPld): IO[Unit] =
    WeakAsync.liftK[IO, ConnectionIO].use { liftIO =>
      transact {
        for {
          _ <-
            NonEmptyList.fromList(List(
                editTaskPld.name.map(name => fr"name = $name"),
                editTaskPld.deadline.map(deadline => fr"deadline = $deadline"),
                editTaskPld.priority.map(priority => fr"priority = $priority")
            ).flatten)
              .map(comma(_))
              .map(updates => fr"UPDATE task SET $updates WHERE id = $taskId".update.run)
              .getOrElse(().pure[ConnectionIO])
          _ <- editTaskPld.tags.map(updateTaskTags(taskId, _)).getOrElse(().pure[ConnectionIO])
          _ <-
            editTaskPld.description
              .map {
                case Some(description) => descriptionService.updateDescription(taskId, description)
                case None => descriptionService.deleteDescription(taskId)
              }
              .map(liftIO(_))
              .getOrElse(().pure[ConnectionIO])
        } yield ()
      }
    }

}

object PgDbService extends StrictLogging {

  def apply(config: doobie.hikari.Config, descriptionService: DescriptionService): Resource[IO, PgDbService] =
    HikariTransactor.fromConfig[IO](
      config,
      logHandler = Some(
        (event: LogEvent) => for {
          queryLog <- IO.pure(s"Query: ${event.sql}, Values: ${event.args}")
          _ <- event match {
            case _: Success => logger.infoIO(queryLog)
            case execFailure: ExecFailure => logger.errorIO(queryLog, execFailure.failure)
            case processingFailure: ProcessingFailure => logger.errorIO(queryLog, processingFailure.failure)
          }
        } yield ()
      )
    )
      .map(transactor => new PgDbService(transactor, descriptionService))

}