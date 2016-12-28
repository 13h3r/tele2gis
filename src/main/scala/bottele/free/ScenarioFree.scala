package bottele.free

import bottele.TelegramBotAPI.{CallbackId, ReplyTo, UserId}
import bottele.free.Reply.ReplyA
import bottele.services.SapphireService
import cats.free.{Free, Inject}
import cats.~>

sealed trait Finish
object Finish extends Finish


object Scenario {
  import cats.free._
  type Algebra[T] = Free[ScenarioFree, T]

  sealed trait ScenarioFree[T]

  final case class CitySelection(text: String, reply: ReplyTo, userId: UserId) extends ScenarioFree[Finish]
  final case class ShowCurrentCity(reply: ReplyTo, userId: UserId) extends ScenarioFree[Finish]
  final case class ShowCard(
    reply: ReplyTo,
    obj: SapphireService.Item,
    callbackId: Option[CallbackId]
  ) extends ScenarioFree[Finish]
  final case class Search(text: String, reply: ReplyTo, userId: UserId) extends ScenarioFree[Finish]
  case object UnexpectedScenario extends ScenarioFree[Finish]

  def citySelection(text: String, reply: ReplyTo, userId: UserId): Algebra[Finish] = {
    Free.liftF(CitySelection(text, reply, userId))
  }
  def showCurrentCity(reply: ReplyTo, userId: UserId): Algebra[Finish] = {
    Free.liftF(ShowCurrentCity(reply, userId))
  }

  def showCard(reply: ReplyTo, obj: SapphireService.Item, callbackId: Option[CallbackId]): Algebra[Finish] = {
    Free.liftF(ShowCard(reply, obj, callbackId))
  }
  def search(text: String, reply: ReplyTo, userId: UserId): Algebra[Finish] = {
    Free.liftF(Search(text, reply, userId))
  }

  val unexpectedScenario: Algebra[Finish] = Free.liftF(UnexpectedScenario)
}

object Testssss {

  def test[M[_]](user: UserId)(
    implicit webAPI: Inject[M, WebAPI.Algebra],
    sapphire: Inject[M, Sapphire.Algebra],
    userStorage: Inject[M, UserStorage.Algebra]
  ): M[Unit] = {

    for {
      city <- UserStorage.getCity(user)
    } yield ()

  }
}

object ToStringInterpreter extends (Scenario.ScenarioFree ~> ReplyA) {
  import Scenario._
  override def apply[A](fa: ScenarioFree[A]): ReplyA[A] = fa match {
    case ShowCurrentCity(reply, user) => Reply.text(reply, reply.toString)
    case CitySelection(_, reply, user) => Reply.text(reply, reply.toString)
    case Search(t, reply, user) => Reply.text(reply, t)
    case ShowCard(reply, user, _) => Reply.text(reply, reply.toString)
  }
}