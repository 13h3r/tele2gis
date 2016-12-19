package bottele.free

import bottele.TelegramBotAPI.{CallbackQuery, ChatId, ReplyTo, Update, UserId}
import bottele.free.ScenarioAlgebra.ScenarioA
import bottele.scenarios.ItemSerializer
import cats.data.Kleisli
import cats.free.Free
import cats.~>

sealed trait IncomingMessage[T]

final case class TelegramUpdate(update: Update) extends IncomingMessage[Finish]
final case class TelegramUpdate2(update: Update) extends IncomingMessage[Finish]

object IncomingMessageAlgebra {
  import cats.free.Free._
  import cats.free._

  type IncomingMessageA[T] = Free[IncomingMessage, T]

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


object IncomingMessageScenarioI {
  type X[T] = Either[String, ScenarioA[T]]
  type R[T] = X[T]
  val instance = new (IncomingMessage ~> R) {
    override def apply[A](fa: IncomingMessage[A]): R[A] = fa match {
      case TelegramUpdate(update) =>
        import TelegramExtractors._
        import cats.instances.option._
        val citySelection: Kleisli[Option, Update, ScenarioA[Finish]] = for {
          user <- messageUserId
          chat <- messageChatId
          text <- nonEmptyCommand("city")
        } yield ScenarioAlgebra.citySelection(text, ReplyTo.fromChat(chat), user)
        val currentCity = for {
          user <- messageUserId
          chat <- messageChatId
        } yield ScenarioAlgebra.showCurrentCity(ReplyTo.fromChat(chat), user)
        val search = for {
          user <- messageUserId
          chat <- messageChatId
          text <- messageText
        } yield ScenarioAlgebra.search(text, ReplyTo.fromChat(chat), user)
        val showCard = for {
          cb <- callback
        } yield ScenarioAlgebra.showCard(
          ReplyTo.fromUser(cb.from.id), ItemSerializer.from(cb.data), Some(cb.id)
        )

        Iterator[Kleisli[Option, Update, ScenarioA[Finish]]](citySelection)
          .map(_.run(update))
          .filter(_.isDefined)
          .map(_.get)
          .take(1)
          .toList
          .headOption
          .map(Right(_))
          .getOrElse(Left(s"Unknown message $update"))
    }
  }
}