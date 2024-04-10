package com.gopas.todolist

import com.gopas.todolist.Model._
import io.circe.DecodingFailure.Reason.CustomReason
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.estatico.newtype.Coercible
import io.estatico.newtype.ops._

import java.util.UUID

object CirceCodecs {

  implicit def newtypeEncoder[A, B](implicit co: Coercible[A, B], enc: Encoder[B]): Encoder[A] = (in: A) => enc(in.coerce)
  implicit def newtypeDecoder[A, B](implicit co: Coercible[B, A], dec: Decoder[B]): Decoder[A] = (c: HCursor) => dec(c).map(_.coerce)

  // override to enforce validation
  implicit val nameDecoder: Decoder[Name] = Decoder[String].map(Name.apply)
  implicit val descriptionDecoder: Decoder[Description] = Decoder[String].map(Description.apply)
  implicit val tagDecoder = Decoder[String].map(Tag.apply)

  implicit val uuidEncoder: Encoder[UUID] = (uuid: UUID) => Json.fromString(uuid.toString)
  implicit val uuidDecoder: Decoder[UUID] = (c: HCursor) => c.value.as[String].map(UUID.fromString)

  implicit val priorityDecoder: Decoder[Priority.Value] = Decoder.decodeEnumeration(Priority)
  implicit val priorityEncoder: Encoder[Priority.Value] = Encoder.encodeEnumeration(Priority)

  implicit def dateTimeRangeDecoder[T]: Decoder[DateTimeRange[T]] =
    Decoder.forProduct2("from", "to")(DateTimeRange.apply _)

  implicit val priorityRangeDecoder: Decoder[PriorityRange] =
    Decoder.forProduct2("from", "to")(PriorityRange.apply _)

  implicit val pagingDecoder: Decoder[Paging] =
    Decoder.forProduct2("from", "count")(Paging.apply _)

  implicit val taskFilterEncoder: Encoder[TaskFilter] = (_: TaskFilter) => throw new NotImplementedError("")

  implicit val orderByDecoder: Decoder[OrderBy] = Decoder[String].map {
    case OrderBy.Priority.sql => OrderBy.Priority
    case OrderBy.Deadline.sql => OrderBy.Deadline
  }

  // allow missing optional value
  implicit def optionEncoder[T](implicit enc: Encoder[T]): Encoder[Option[T]] = {
    case Some(value) => value.asJson.deepDropNullValues
    case None => Json.Null.deepDropNullValues
  }
  implicit def optionDecoder[T](implicit dec: Decoder[T]): Decoder[Option[T]] = Decoder.withReattempt {
    case c: FailedCursor if !c.incorrectFocus => Right(Option.empty[T])
    case c: HCursor if c.value.isNull => Right(Option.empty[T])
    case c: HCursor => dec(c).map(Some(_))
    case c => Left(DecodingFailure(CustomReason("invalid cursor value on option"), c))
  }

}