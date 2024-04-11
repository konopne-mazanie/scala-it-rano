package com.gopas.todolist

import com.gopas.todolist.Model._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.codec.newtype._
import CirceCodecs._
import TapirCodecs._
import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams

object Endpoints {

  private val todoListEndpoint =
    endpoint
      .securityIn(header[AccessToken]("token"))
      .tag("TODO List")
      .in("api" / "task")
      .errorOut(statusCode.and(plainBody[String]))
      .out(statusCode)

  private val idPath = path[Id[Task]]("id")

  val getTaskList =
    todoListEndpoint
      .post
      .in("list")
      .in(jsonBody[TaskFilter])
      .out(jsonBody[TaskList])

  val getTask =
    todoListEndpoint
      .get
      .in(idPath)
      .out(jsonBody[TaskDetail])

  val deleteTask =
    todoListEndpoint
      .delete
      .in(idPath)

  val createTask =
    todoListEndpoint
      .post
      .in("new")
      .in(jsonBody[CreateTaskPld])
      .out(plainBody[Id[Task]])

  val editTask =
    todoListEndpoint
      .put
      .in(idPath)
      .in(jsonBody[EditTaskPld])

  val setTaskDone =
    todoListEndpoint
      .put
      .in(idPath)
      .in("done")

  val unsetTaskDone =
    todoListEndpoint
      .delete
      .in(idPath)
      .in("done")

  val websocket =
    endpoint
      .in("api" / "ws")
      .out(webSocketBody[WSRequest, CodecFormat.Json, Task, CodecFormat.Json](Fs2Streams[IO]))

  val endpoints = List(getTaskList, getTask, deleteTask, createTask, editTask, setTaskDone, unsetTaskDone)

}