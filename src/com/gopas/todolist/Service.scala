package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.gopas.todolist.Model._
import com.typesafe.scalalogging.StrictLogging
import fs2._

class Service(pgDbService: PgDbService, mongoDbService: MongoDbService) extends StrictLogging {

  private val exportChunks: Int = 10

  def getTaskList: TaskFilter => IO[TaskList] = pgDbService.getTasks

  def getTaskDetail: Id[Task] => IO[TaskDetail] = mongoDbService.getTaskDetail

  def deleteTask: Id[Task] => IO[Unit] = pgDbService.deleteTask

  def createTask: CreateTaskPld => IO[Id[Task]] = pgDbService.createTask

  def editTask: (Id[Task], EditTaskPld) => IO[Unit] = pgDbService.editTask

  def setTaskDone: (Id[Task], IsDone) => IO[Unit] = pgDbService.setTaskDone

  def exportTasks: Pipe[IO, WSRequest, Task] =
    _.repeatPull(_.uncons1.flatMap {
      case Some((WSRequest(tags), tailStream)) =>
        Pull.loop[IO, Task, Int](offset =>
          Pull.eval(
              pgDbService.getTasks(TaskFilter(
                tagFilter = tags,
                paging = Paging(offset, exportChunks),
                ordering = OrderingDef(OrderBy.Deadline, ascending = false)
              ))
            )
            .flatMap { case TaskList(tasks, _) =>
              Pull.output(Chunk.from(tasks)) >>
                Pull.pure(if (tasks.length < exportChunks) Option.empty else Some(offset + exportChunks))
            }
        )(0) >> Pull.pure(Some(tailStream))
      case None =>
        Pull.pure(Option.empty)
    })

}
