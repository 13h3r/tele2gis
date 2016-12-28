package bottele.free

import bottele.TelegramBotAPI.{CallbackQuery, ChatId, ReplyTo, Update, UserId}
import bottele.free.Scenario.Algebra
import bottele.scenarios.ItemSerializer
import cats.data.Kleisli
import cats.free.Free
import cats.~>


object IncomingMessage {
  import cats.free.Free._
  import cats.free._

  sealed trait IncomingMessageFree[T]

  final case class TelegramUpdate(update: Update) extends IncomingMessageFree[Finish]

  type IncomingMessageA[T] = Free[IncomingMessageFree, T]

  def telegramUpdate(update: Update): IncomingMessageA[Finish] = {
    liftF(TelegramUpdate(update))
  }
}

object TelegramExtractors {
  type Extractor[T] = Kleisli[Option, Update, T]

  val messageUserId: Extractor[UserId] = Kleisli(_.message.flatMap(_.from.map(_.id)))
  val messageChatId: Extractor[ChatId] = Kleisli(_.message.map(_.chat.id))
  val messageText: Extractor[String] = Kleisli(_.message.flatMap(_.text))

  def command(name: String): Extractor[String] = {
    val size = name.length + 1
    Kleisli((u: Update) =>
      for {
        m <- u.message
        text <- m.text
        if text.startsWith(s"/$name")
      } yield text.substring(size).trim
    )
  }

  def nonEmptyCommand(name: String): Extractor[String] = command(name).mapF(_.filter(!_.isEmpty))
  def emptyCommand(name: String): Extractor[String] = command(name).mapF(_.filter(_.isEmpty))

  val callback: Extractor[CallbackQuery] = Kleisli(_.callbackQuery)
}

object IncomingMessageInterpreter {
  import IncomingMessage._
  val instance: IncomingMessageFree ~> Algebra = Lambda[IncomingMessageFree ~> Algebra]({
    case TelegramUpdate(update) =>
      import TelegramExtractors._
      import cats.instances.option._
      val citySelection = for {
        user <- messageUserId
        chat <- messageChatId
        text <- nonEmptyCommand("city")
      } yield Scenario.citySelection(text, ReplyTo.fromChat(chat), user)
      val currentCity = for {
        user <- messageUserId
        chat <- messageChatId
      } yield Scenario.showCurrentCity(ReplyTo.fromChat(chat), user)
      val search = for {
        user <- messageUserId
        chat <- messageChatId
        text <- messageText
      } yield Scenario.search(text, ReplyTo.fromChat(chat), user)
      val showCard = for {
        cb <- callback
      } yield Scenario.showCard(
        ReplyTo.fromUser(cb.from.id), ItemSerializer.from(cb.data), Some(cb.id)
      )

      Iterator(citySelection, currentCity, search, showCard)
        .map(_.run(update))
        .filter(_.isDefined)
        .map(_.get)
        .take(1)
        .toList
        .headOption
        .getOrElse(Scenario.unexpectedScenario)
    })
}