package bottele.scenarios

import akka.Done
import akka.stream.stage.GraphStageLogic.SubSourceOutlet
import bottele.{TelegramBotAPI, WebAPI}
import bottele.TelegramBotAPI._
import bottele.WebAPI.ApiError

import scala.concurrent.{ExecutionContext, Future}

object Router {
  def apply(update: Update)(implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI): Future[Done] = update match {
    case u if u.message.isDefined && u.message.get.text.isDefined =>
      Search(u.message.get.chat.id, u.message.get.text.get)
    case Update(_ ,_ ,_, Some(callback)) =>
      Info(callback.from.id, callback.data, Some(callback.id.toLong))
    case unknown =>
      Future.successful(Done)
  }
}

object Info {
  trait Reply
  case class Location(lon: Double, lat: Double)
  case class Info(name: String, phones: Iterable[String], location: Option[Location]) extends Reply
  object NotFound extends Reply

  def apply(chatId: Long, filial: String, callbackId: Option[Long])
    (implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI
  ): Future[Done] = {
    webApi
      .branches(Seq(filial.toLong))
      .map { branches =>
        branches match {
          case Left(ApiError(404, _, _)) => NotFound
          case Left(error) => throw error
          case Right(branches) =>
            branches.items.headOption.map { branch =>
              val phones = for {
                groups <- branch.contact_groups.toSeq.flatten
                contact <- groups.contacts
                if contact.`type` == "phone"
              } yield contact.text
              val location = branch.point.map(p => Location(p.lon, p.lat))
              Info(branch.name, phones, location)
            }.getOrElse(NotFound)
        }
      }
      .map {
        case NotFound => teleApi.sendMessage(SendMessage(chatId, "Увы, мы ничего не нашли"))
        case Info(name, phones, location) =>
          val phonesText = Option(phones)
            .filter(_.nonEmpty)
            .map(_.mkString(", "))
            .map(p => s"Позвонить: $p\n")
            .getOrElse("")
          val text = s"$name\n$phonesText"

          for {
            callbackF <- callbackId.map(teleApi.answerCallbackQuery(_, None)).getOrElse(Future.successful(()))
            msgF <- teleApi.sendMessage(SendMessage(chatId, text))
            locatF <- location.map {
              case Location(lon, lat) =>
                teleApi.sendLocation(SendLocation(chatId, lat, lon))
            }.getOrElse(Future.successful(Done))
          } yield Done
      }
      .map(_ => Done)
  }

}

object Search {
  def apply(chatId: Long, text: String)(implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI): Future[Done] = {
    case class Reply(chatId: Long, response: Response)
    trait Response
    object UnknownUpdate extends Response
    object NotFound extends Response
    case class Firm(id: String, name: String)
    case class Firms(firms: List[Firm]) extends Response

    def renderReply(reply: Reply) = reply.response match {
      case UnknownUpdate => SendMessage(reply.chatId, "Какое-то непонятное сообщение")
      case NotFound => SendMessage(reply.chatId, "Увы, мы ничего не нашли")
      case Firms(firms) =>
        val markup = InlineKeyboardMarkup(
          List(firms.zipWithIndex.map { case (f, i) => InlineKeyboardButton(s"${i + 1}", None, Some(f.id)) })
        )
        val text = firms.zipWithIndex.map {
          case (f, i) => s"${i + 1}. ${f.name}"
        }.mkString("\n")
        SendMessage(reply.chatId, text, Some(markup))
    }

    webApi
      .searchBranches(text, 1, page = Some(1), pageSize = Some(5))
      .map { search =>
        if (search.items.isEmpty) {
          NotFound
        }
        else {
          Firms(
            search.items.toList.map { branch =>
              val address = branch.address_name.map(address => s" ($address)").getOrElse("")
              val id = branch.id.split('_').head
              Firm(id, s"${branch.name}$address")
            }
          )
        }
      }
      .map {
        case Firms(firm :: Nil) => Info(chatId, firm.id, None)
        case reply => teleApi.sendMessage(renderReply(Reply(chatId, reply)))
      }
      .map(_ => Done)
  }
}
