package com.gopas.todolist

import com.gopas.todolist.Model.Priority.Priority
import com.gopas.todolist.Model._
import sttp.tapir._
import sttp.tapir.generic.auto._

import java.time.LocalDateTime
import java.util.UUID
import scala.util.{Failure, Success, Try}

object TapirCodecs {

  private def rangeSchema[R, FT](getFromFn: R => FT, getToFn: R => FT)(implicit fromToSchema: Schema[FT]): Schema[R] =
    Schema(SchemaType.SProduct(List(
      SchemaType.SProductField[R, FT](FieldName("from"), fromToSchema, getFromFn.andThen(Some(_))),
      SchemaType.SProductField[R, FT](FieldName("to"), fromToSchema, getToFn.andThen(Some(_)))
    )))

  implicit def dateTimeRangeSchema[T]: Schema[DateTimeRange[T]] =
    rangeSchema[DateTimeRange[T], LocalDateTime](_.from, _.to)

  implicit val priorityRangeSchema: Schema[PriorityRange] =
    rangeSchema[PriorityRange, Priority](_.from, _.to)

  implicit val pagingSchema: Schema[Paging] =
    rangeSchema[Paging, Int](_.from, _.count)

  implicit def idCodec[T]: Codec[String, Id[T], CodecFormat.TextPlain] =
    Codec.string.mapDecode(id => Try(UUID.fromString(id)).map(Id[T]) match {
      case Success(value) => DecodeResult.Value(value)
      case Failure(exception) => DecodeResult.Error("Unable to decode ID", exception)
    })(_.value.toString)

}