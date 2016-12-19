package bottele.free

import bottele.TelegramBotAPI.{CallbackQuery, ChatId, ReplyTo, Update, UserId}
import bottele.free.ScenarioAlgebra.ScenarioA
import bottele.scenarios.ItemSerializer
import cats.data.Kleisli
import cats.~>

sealed trait MessageParsing[T]

object MessageParsingAlgebra {
  import cats.free._
  import cats.free.Free._
  case object MUserId extends MessageParsing[UserId]
  case object MChatId extends MessageParsing[ChatId]
//  case object MMessageText extends MessageParsing[String]
//  final case class MEmptyCommand(name: String) extends MessageParsing[Unit]
//  final case class MNonEmptyCommand(name: String) extends MessageParsing[String]
//  case object MCallback extends MessageParsing[CallbackQuery]

  type MessageParsingA[T] = Free[MessageParsing, T]
  val userId: MessageParsingA[UserId] = liftF(MUserId)
  val chatId: MessageParsingA[ChatId] = liftF(MChatId)
//  val messageText: MessageParsingA[String] = liftF(MMessageText)
//  val callback: MessageParsingA[CallbackQuery] = liftF(MCallback)
//  def nonEmptyCommand(name: String): MessageParsingA[String] = liftF(MNonEmptyCommand(name))
//  def emptyCommand(name: String): MessageParsingA[Unit] = liftF(MEmptyCommand(name))
}

class MessageParsingInterprenter {
  import MessageParsingAlgebra._
  def apply(update: Update): MessageParsing ~> Option = new (MessageParsing ~> Option) {
    override def apply[A](fa: MessageParsing[A]): Option[A] = fa match {
//      case _ => None
      case MUserId => update.message.flatMap(_.from).map(_.id)
      case MChatId => update.message.map(_.chat.id)
    }
  }
}



object XXXXXXXX {
  import MessageParsingAlgebra._
//  val citySelection = for {
//    user <- userId
//    chat <- chatId
//    text <- nonEmptyCommand("city")
//  } yield ScenarioAlgebra.citySelection(text, ReplyTo.fromChat(chat), user)
//  val currentCity = for {
//    user <- userId
//    chat <- chatId
//    _ <- emptyCommand("city")
//  } yield ScenarioAlgebra.showCurrentCity(ReplyTo.fromChat(chat), user)
//  val search = for {
//    user <- userId
//    chat <- chatId
//    text <- messageText
//  } yield ScenarioAlgebra.search(text, ReplyTo.fromChat(chat), user)
//  val showCard = for {
//    cb <- callback
//  } yield ScenarioAlgebra.showCard(
//    ReplyTo.fromUser(cb.from.id), ItemSerializer.from(cb.data), Some(cb.id)
//  )

}


