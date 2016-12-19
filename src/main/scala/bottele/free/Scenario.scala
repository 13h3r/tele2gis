package bottele.free

import bottele.TelegramBotAPI.{CallbackId, ReplyTo, UserId}
import bottele.services.SapphireService
import cats.~>

sealed trait Finish
object Finish extends Finish

sealed trait Scenario[T]

final case class CitySelection(text: String, reply: ReplyTo, userId: UserId) extends Scenario[Finish]
final case class ShowCurrentCity(reply: ReplyTo, userId: UserId) extends Scenario[Finish]
final case class ShowCard(
  reply: ReplyTo,
  obj: SapphireService.Item,
  callbackId: Option[CallbackId]
) extends Scenario[Finish]
final case class Search(text: String, reply: ReplyTo, userId: UserId) extends Scenario[Finish]
case object UnexpectedScenario extends Scenario[Finish]

object ScenarioAlgebra {
  import cats.free._
  type ScenarioA[T] = Free[Scenario, T]

  def citySelection(text: String, reply: ReplyTo, userId: UserId): ScenarioA[Finish] = {
    Free.liftF(CitySelection(text, reply, userId))
  }
  def showCurrentCity(reply: ReplyTo, userId: UserId): ScenarioA[Finish] = {
    Free.liftF(ShowCurrentCity(reply, userId))
  }

  def showCard(reply: ReplyTo, obj: SapphireService.Item, callbackId: Option[CallbackId]): ScenarioA[Finish] = {
    Free.liftF(ShowCard(reply, obj, callbackId))
  }
  def search(text: String, reply: ReplyTo, userId: UserId): ScenarioA[Finish] = {
    Free.liftF(Search(text, reply, userId))
  }

  val unexpectedScenario: ScenarioA[Finish] = Free.liftF(UnexpectedScenario)
}

object ToStringInterpreter extends (Scenario ~> Reply) {
  override def apply[A](fa: Scenario[A]): Reply[A] = fa match {
    case ShowCurrentCity(reply, user) => Text(reply, reply.toString)
    case CitySelection(_, reply, user) => Text(reply, reply.toString)
    case Search(t, reply, user) => Text(reply, t)
    case Unknow(t, reply, user) => Text(reply, t)
  }
}