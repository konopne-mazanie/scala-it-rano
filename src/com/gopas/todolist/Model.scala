package com.gopas.todolist

import com.gopas.todolist.Model.Priority.Priority
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._

import java.time.LocalDateTime
import java.util.UUID
import scala.util.matching.Regex

object Model {

  private def validateName(fieldName: String, validationRegex: Regex, value: String): Unit =
    value match {
      case validationRegex(_) => ()
      case _ => throw new IllegalArgumentException(s"Invalid $fieldName")
    }

  @newtype case class Id[T](value: UUID)

  @newtype class Name(val value: String)
  object Name {
    def apply(value: String): Name = {
      validateName("name", "^[A-Za-z0-9\\s]{1,20}$".r, value)
      value.coerce
    }
  }

  @newtype class Description(val value: String)
  object Description {
    def apply(value: String): Description = {
      validateName("description", "^[A-Za-z0-9\\s-:\n]{1,500}$".r, value)
      value.coerce
    }
  }

  @newtype class Tag(val value: String)
  object Tag {
    def apply(value: String): Tag = {
      validateName("tag", "^[A-Za-z0-9]{1,10}$".r, value)
      value.coerce
    }
  }

  @newtype case class IsDone(value: Boolean)

  @newtype case class Deadline(value: LocalDateTime)

  object Priority extends Enumeration {
    type Priority = Value
    val Low, Medium, High = Value
  }

  case class Task(
                 id: Id[Task],
                 name: Name,
                 tags: Set[Tag],
                 isDone: IsDone,
                 deadline: Deadline,
                 priority: Priority
                 )

  case class TaskDetail(id: Id[Task], description: Description)

  case class CreateTaskPld(
                   name: Name,
                   tags: Set[Tag],
                   deadline: Deadline,
                   priority: Priority,
                   description: Option[Description]
                 )

  case class EditTaskPld(
                            name: Option[Name],
                            tags: Option[Set[Tag]],
                            deadline: Option[Deadline],
                            priority: Option[Priority],
                            description: Option[Option[Description]]
                          )

  class DateTimeRange[T](val from: LocalDateTime, val to: LocalDateTime)
  object DateTimeRange {
    def apply[T](from: LocalDateTime, to: LocalDateTime): DateTimeRange[T] = {
      if (from.isAfter(to))
        throw new IllegalArgumentException(s"Invalid range, to: $to should be after from: $from")
      new DateTimeRange[T](from, to)
    }
  }

  class PriorityRange(val from: Priority, val to: Priority)
  object PriorityRange {
    def apply(from: Priority, to: Priority): PriorityRange = {
      if (from > to)
        throw new IllegalArgumentException(s"Invalid range, to: $to should be >= than from: $from")
      new PriorityRange(from, to)
    }
  }

  class Paging(val from: Int, val count: Int)
  object Paging {
    def apply(from: Int, count: Int): Paging = {
      if ((from < 0) && (count <= 0))
        throw new IllegalArgumentException(s"Invalid paging.")
      new Paging(from, count)
    }
  }

  case class TaskFilter(
                       nameFilter: Option[Name],
                       tagFilter: Set[Tag],
                       isDoneFilter: Option[IsDone],
                       deadlineFilter: Option[DateTimeRange[Deadline]],
                       priorityFilter: PriorityRange,
                       paging: Paging
                       )

  case class TaskList(tasks: List[Task], totalCount: Int)

  @newtype case class AccessToken(value: String)

}