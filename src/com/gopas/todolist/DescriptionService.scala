package com.gopas.todolist

import cats.effect._
import com.gopas.todolist.Model.{Description, Id, Task}

trait DescriptionService {

  def insertDescription(taskId: Id[Task], description: Description): IO[Unit]

  def updateDescription(taskId: Id[Task], description: Description): IO[Unit]

  def deleteDescription(taskId: Id[Task]): IO[Unit]

}
