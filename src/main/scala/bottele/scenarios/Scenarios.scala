package bottele.scenarios

import akka.Done
import bottele.TelegramBotAPI._
import bottele.WebAPI.RegionInfoPayload
import bottele.userstorage.UserStorage
import bottele.{City, TelegramBotAPI, WebAPI}
import cats.data.EitherT

import scala.concurrent.{ExecutionContext, Future}

object Router extends Telerouting {
  def apply(update: Update)(implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI, storage: UserStorage): Future[Any] = {

    val route =
      {
        messageUserId { user =>
          messageChatId { chat =>
            nonEmptyCommand("city") { text =>
              asyncResult(
                CitySelectionScenario(text, chat, user)
              )
            } ~
            emptyCommand("city") { _ =>
              asyncResult(
                ShowCurrentCity(chat, user)
              )
            } ~
            messageText { text =>
              asyncResult {
                SearchScenario(chat, user, text)
              }
            }
          }
        } ~
        callback { cb =>
          asyncResult {
            InfoScenario(Right(cb.from.id), cb.data, Some(cb.id))
          }
        }
      }

    route(update) match {
      case Left(_) =>
        println("Unmatched update")
        Future.successful(())
      case Right(r) => r
    }
  }
}

object CitySelectionScenario {
  def apply(text: String, chatId: ChatId, userId: UserId)(
    implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI, storage: UserStorage): Future[Message] =
  {
    import TelegramBotAPI._
    import cats.instances.future._

    EitherT.pure[Future, SendMessage, String](text)
      .ensure(SendMessage(Left(chatId), "Введите не менее двух симолов"))(_.length > 2)
      .semiflatMap(webApi.regionSearch)
      .ensure(SendMessage(Left(chatId), "Увы, мы ничего не нашли"))(_.items.nonEmpty)
      .subflatMap { regions =>
        if(regions.items.length == 1) {
          Right(regions.items.head)
        } else {
          Left(
            SendMessage(Left(chatId), s"Мы нашли такие города: ${regions.items.map(_.name).mkString(", ")}")
          )
        }
      }
      .semiflatMap { region =>
        storage.setCity(userId, City(region.id.toInt)).map { x =>
          SendMessage(Left(chatId), s"Ваш новый город ${region.name}")
        }
      }
      .valueOr(identity)
      .flatMap(teleApi.sendMessage)
  }
}

object ShowCurrentCity {
  def apply(chat: ChatId, user: UserId)(implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI, storage: UserStorage): Future[Message] = {
    import cats.instances.future._
    EitherT
      .right(storage.getCity(user))
      .subflatMap {
        case None => Left(SendMessage(Left(chat), "Вы еще не выбрали город"))
        case Some(city) => Right(city.id)
      }
      .semiflatMap(webApi.regionGet)
      .subflatMap {
        case RegionInfoPayload(Nil) => Left(SendMessage(Left(chat), "Не удалось найти ваш город"))
        case RegionInfoPayload(region :: Nil) => Right(SendMessage(Left(chat), s"Ваш текущий город ${region.name}"))
        case _ => Left(SendMessage(Left(chat), "Не удалось найти ваш город"))
      }
      .valueOr(identity)
      .flatMap(teleApi.sendMessage)
  }
}

object InfoScenario {
  trait Reply
  case class Location(lon: Double, lat: Double)
  case class Info(name: String, address: Option[String], phones: Iterable[String], location: Option[Location]) extends Reply
  object NotFound extends Reply

  def apply(chatId: Either[ChatId, UserId], filial: String, callbackId: Option[CallbackId])
           (implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI
           ): Future[Done] = {
    webApi
      .branches(Seq(filial.toLong))
      .map { branches =>
        if(branches.items.isEmpty) NotFound
        else {
          branches.items.headOption.map { branch =>
            val phones = for {
              groups <- branch.contact_groups.toSeq.flatten
              contact <- groups.contacts
              if contact.`type` == "phone"
            } yield contact.text
            val location = branch.point.map(p => Location(p.lon, p.lat))
            Info(branch.name, branch.address_name, phones, location)
          }.getOrElse(NotFound)
        }
      }
      .map {
        case NotFound => teleApi.sendMessage(SendMessage(chatId, "Увы, мы ничего не нашли"))
        case Info(name, address, phones, location) =>
          val phonesText = Option(phones)
            .filter(_.nonEmpty)
            .map(_.mkString(", "))
            .map(p => s"\nТелефон: $p")
            .getOrElse("")
          val addressText = address.map("\nАдрес: " + _).getOrElse("")
          val text = s"$name$addressText$phonesText"

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

object SearchScenario {
  def apply(chatId: ChatId, userId: UserId, text: String)(implicit ec: ExecutionContext, teleApi: TelegramBotAPI, webApi: WebAPI, storage: UserStorage): Future[Done] = {
    case class Reply(chatId: ChatId, response: Response)
    trait Response
    object UnknownUpdate extends Response
    object NotFound extends Response
    case class Firm(id: String, name: String)
    case class Firms(firms: List[Firm]) extends Response

    def renderReply(reply: Reply) = reply.response match {
      case UnknownUpdate => SendMessage(Left(reply.chatId), "Какое-то непонятное сообщение")
      case NotFound => SendMessage(Left(reply.chatId), "Увы, мы ничего не нашли")
      case Firms(firms) =>
        val markup = InlineKeyboardMarkup(
          List(firms.zipWithIndex.map { case (f, i) => InlineKeyboardButton(s"${i + 1}", None, Some(f.id)) })
        )
        val text = firms.zipWithIndex.map {
          case (f, i) => s"${i + 1}. ${f.name}"
        }.mkString("\n")
        SendMessage(Left(reply.chatId), text, Some(markup))
    }

    storage
      .getCity(userId)
      .map(_.map(_.id).getOrElse(1))
      .flatMap(webApi.searchBranches(text, _, page = Some(1), pageSize = Some(5)))
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
        case Firms(firm :: Nil) => InfoScenario(Left(chatId), firm.id, None)
        case reply => teleApi.sendMessage(renderReply(Reply(chatId, reply)))
      }
      .map(_ => Done)
  }
}
