package bottele.free

import bottele.TelegramBotAPI.{CallbackId, ReplyTo, UserId}
import bottele.services.SapphireService

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
}