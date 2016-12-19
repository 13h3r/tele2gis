package test

import cats.free.Free._
import cats.free._
import cats.~>

sealed trait Envelope[T]

case object StringEnvelope extends Envelope[String]

object EnvelopeAlgebra {
  val string: Free[Envelope, String] = liftF(StringEnvelope)
}

object Interpreter {
  def apply() = new (Envelope ~> Option) {
    override def apply[A](fa: Envelope[A]): Option[A] = fa match {
      case StringEnvelope => List(Option("asd"))
        .collect {
          case Some(x) => x
        }
        // if I comment this line code compiles
        .take(1)
        .headOption
    }
  }
}