package com.gopas.todolist

import com.gopas.todolist.Model.Priority.Priority
import com.gopas.todolist.Model._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import io.estatico.newtype.Coercible
import io.estatico.newtype.ops.toCoercibleIdOps

import java.util.UUID
import scala.reflect.ClassTag

object DoobieCodecs {

  implicit def newtypeRead[A, B](implicit co: Coercible[B, A], read: Read[B]): Read[A] = Read[B].map(_.coerce)
  implicit def newtypePut[A, B](implicit co: Coercible[A, B], put: Put[B]): Put[A] = Put[B].contramap[A](_.coerce)

  implicit def setRead[T](implicit read: Read[Array[T]]): Read[Set[T]] = read.map(_.toSet)
  implicit def setPut[T](implicit ct: ClassTag[T], put: Put[Array[T]]): Put[Set[T]] = put.contramap(_.toArray)

  implicit def idRead[T]: Read[Id[T]] = Read[UUID].map(Id[T])
  implicit def idPut[T]: Put[Id[T]] = Put[UUID].contramap(_.value)

  implicit val priorityRead: Read[Priority] = Read[Int].map(Priority(_))
  implicit val priorityPut: Put[Priority] = Put[Int].contramap(_.id)

}
