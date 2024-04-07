package com.gopas.todolist

import cats.effect._
import cats.implicits._
import com.gopas.todolist.Model._
import com.typesafe.scalalogging.StrictLogging

class Service(pgDbService: PgDbService, mongoDbService: MongoDbService) extends StrictLogging {

  def getTaskList: TaskFilter => IO[TaskList] = pgDbService.getTasks

  def getTaskDetail: Id[Task] => IO[TaskDetail] = mongoDbService.getTaskDetail

  def deleteTask: Id[Task] => IO[Unit] = pgDbService.deleteTask

  def createTask: CreateTaskPld => IO[Id[Task]] = pgDbService.createTask

  def editTask: (Id[Task], EditTaskPld) => IO[Unit] = pgDbService.editTask

  def setTaskDone: (Id[Task], IsDone) => IO[Unit] = pgDbService.setTaskDone

}
