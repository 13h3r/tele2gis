package bottele.free

import bottele.TelegramBotAPI.{CallbackQuery, ChatId, ReplyTo, Update, UserId}
import bottele.scenarios.ItemSerializer
import cats.~>

sealed trait MessageParsing[T]

object MessageParsingAlgebra {
  import cats.free.Free._
  import cats.free._
  object Algebra {
    case object UserId extends MessageParsing[UserId]
    case object ChatId extends MessageParsing[ChatId]
    case object MessageText extends MessageParsing[String]
    final case class EmptyCommand(name: String) extends MessageParsing[Unit]
    final case class NonEmptyCommand(name: String) extends MessageParsing[String]
    case object Callback extends MessageParsing[CallbackQuery]
  }

  type MessageParsingA[T] = Free[MessageParsing, T]
  val userId: MessageParsingA[UserId] = liftF(Algebra.UserId)
  val chatId: MessageParsingA[ChatId] = liftF(Algebra.ChatId)
  val messageText: MessageParsingA[String] = liftF(Algebra.MessageText)
  val callback: MessageParsingA[CallbackQuery] = liftF(Algebra.Callback)
  def nonEmptyCommand(name: String): MessageParsingA[String] = liftF(Algebra.NonEmptyCommand(name))
  def emptyCommand(name: String): MessageParsingA[Unit] = liftF(Algebra.EmptyCommand(name))
}

class MessageParsingInterprenter {
  import MessageParsingAlgebra._
  def apply(update: Update): MessageParsing ~> Option = new (MessageParsing ~> Option) {
    def command(name: String): String => Option[String] = fullText => {
      if(fullText.startsWith(s"/$name")) Option(fullText.substring(name.length + 1))
      else None
    }
    override def apply[A](fa: MessageParsing[A]): Option[A] = fa match {
      case Algebra.UserId => update.message.flatMap(_.from).map(_.id)
      case Algebra.ChatId => update.message.map(_.chat.id)
      case Algebra.MessageText => update.message.flatMap(_.text)
      case Algebra.Callback => update.callbackQuery
      case Algebra.EmptyCommand(name) => update
        .message
        .flatMap(_.text)
        .flatMap(command(name))
        .map(_.trim)
        .filter(_.isEmpty)
        .map(_ => ())
      case Algebra.NonEmptyCommand(name) => update
        .message
        .flatMap(_.text)
        .flatMap(command(name))
        .map(_.trim)
        .filter(!_.isEmpty)
    }
  }
}

object Usage {
  import MessageParsingAlgebra._
  val citySelection = for {
    user <- userId
    chat <- chatId
    text <- nonEmptyCommand("city")
  } yield Scenario.citySelection(text, ReplyTo.fromChat(chat), user)
  val currentCity = for {
    user <- userId
    chat <- chatId
    _ <- emptyCommand("city")
  } yield Scenario.showCurrentCity(ReplyTo.fromChat(chat), user)
  val search = for {
    user <- userId
    chat <- chatId
    text <- messageText
  } yield Scenario.search(text, ReplyTo.fromChat(chat), user)
  val showCard = for {
    cb <- callback
  } yield Scenario.showCard(
    ReplyTo.fromUser(cb.from.id), ItemSerializer.from(cb.data), Some(cb.id)
  )
}


