package bottele.scenarios

import akka.Done
import bottele.TelegramBotAPI._
import bottele.services.{SapphireService, UserStorage, WebAPI}
import bottele.services.WebAPI.RegionInfoPayload
import bottele.{City, TelegramBotAPI}
import cats.data.{EitherT, NonEmptyList}
import com.vividsolutions.jts.geom.{GeometryFactory, Point, PrecisionModel}
import com.vividsolutions.jts.io.WKTReader

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object Router extends Telerouting {
  def apply(update: Update)(
    implicit ec: ExecutionContext,
    teleApi: TelegramBotAPI,
    webApi: WebAPI,
    storage: UserStorage,
    sapphireService: SapphireService
  ): Future[Any] =
  {
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
            InfoScenario(ReplyTo.fromUser(cb.from.id), ItemSerializer.from(cb.data), Some(cb.id))
          }
        }
      }

    route(update) match {
      case Left(_) =>
        println(s"Unmatched update\n$update")
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
      .ensure(SendMessage(ReplyTo.fromChat(chatId), "Введите не менее двух симолов"))(_.length > 2)
      .semiflatMap(webApi.regionSearch)
      .ensure(SendMessage(ReplyTo.fromChat(chatId), "Увы, мы ничего не нашли"))(_.items.nonEmpty)
      .subflatMap { regions =>
        if(regions.items.length == 1) {
          Right(regions.items.head)
        } else {
          Left(
            SendMessage(ReplyTo.fromChat(chatId), s"Мы нашли такие города: ${regions.items.map(_.name).mkString(", ")}")
          )
        }
      }
      .semiflatMap { region =>
        storage.setCity(userId, City(region.id.toInt)).map { x =>
          SendMessage(ReplyTo.fromChat(chatId), s"Ваш новый город ${region.name}")
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
        case None => Left(SendMessage(ReplyTo.fromChat(chat), "Вы еще не выбрали город"))
        case Some(city) => Right(city.id)
      }
      .semiflatMap(id => webApi.regionGet(List(id)))
      .subflatMap {
        case RegionInfoPayload(Nil) => Left(SendMessage(ReplyTo.fromChat(chat), "Не удалось найти ваш город"))
        case RegionInfoPayload(region :: Nil) => Right(SendMessage(ReplyTo.fromChat(chat), s"Ваш текущий город ${region.name}"))
        case _ => Left(SendMessage(ReplyTo.fromChat(chat), "Не удалось найти ваш город"))
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

  def apply(
             reply: ReplyTo,
             obj: SapphireService.Item,
             callbackId: Option[CallbackId])
            (implicit ec: ExecutionContext,
             teleApi: TelegramBotAPI,
             webApi: WebAPI
            ): Future[Done] =
  {
    obj match {
      case SapphireService.Geo(id) =>
        webApi
          .geoGetOne(id)
          .map {
            case None =>
              teleApi.sendMessage(SendMessage(reply, "Увы, мы ничего не нашли"))
            case Some(geoInfo) =>
              val name = {
                val purpose = geoInfo.purpose_name.map(".\n" + _).getOrElse("")
                val text = geoInfo.full_name + purpose
                teleApi.sendMessage(SendMessage(reply, text, None))
              }
              val coordinates = {
                geoInfo.geometry
                  .flatMap(_.centroid)
                  .flatMap(GeometryReader.apply)
                  .map { case (lat, lon) =>
                    teleApi.sendLocation(SendLocation(reply, lat, lon))
                  }
                  .getOrElse(Future.successful(()))
              }

              for {
                _ <- name
                _ <- coordinates
              } yield Done
          }
          .map(_ => Done)
      case SapphireService.Branch(id) =>
        webApi
          .branchGet(Seq(id))
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
            case NotFound => teleApi.sendMessage(SendMessage(reply, "Увы, мы ничего не нашли"))
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
                msgF <- teleApi.sendMessage(SendMessage(reply, text))
                locatF <- location.map {
                  case Location(lon, lat) =>
                    teleApi.sendLocation(SendLocation(reply, lat, lon))
                }.getOrElse(Future.successful(Done))
              } yield Done
          }
          .map(_ => Done)
    }

  }

}

object SearchScenario {
  def apply(chatId: ChatId, userId: UserId, text: String)(
    implicit ec: ExecutionContext,
    teleApi: TelegramBotAPI,
    webApi: WebAPI,
    storage: UserStorage,
    sapphire: SapphireService): Future[Done] =
  {
    def renderSerp(result: NonEmptyList[SapphireService.Item]): Future[SendMessage] = {
      Future.sequence(result
        .toList
        .map {
          case SapphireService.Branch(id) =>
            webApi.branchGetOne(id).map(_.map { branch =>
              val address = branch.address_name.map(address => s" ($address)").getOrElse("")
              s"${branch.name}$address"
            })
          case SapphireService.Geo(id) =>
            webApi.geoGetOne(id).map(_.map { geo =>
              geo.name
            })
        })
        .map { texts =>
          val messageText = texts
            .collect { case Some(x) => x }
            .zipWithIndex.map {
              case (f, i) => s"${i + 1}. $f"
            }.mkString("\n")

          val kb = InlineKeyboardMarkup(
            List(result
              .toList
              .zipWithIndex
              .map {
                case (item, order) =>
                  InlineKeyboardButton(s"${order + 1}", None, Some(ItemSerializer.to(item)))
              }
            )
          )
          SendMessage(ReplyTo.fromChat(chatId), messageText, Some(kb))
        }
    }

    storage
      .getCity(userId)
      .map(_.getOrElse(City(1)))
      .flatMap(sapphire.search(text, _))
      .flatMap {
        case SapphireService.Empty =>
          teleApi
            .sendMessage(SendMessage(ReplyTo.fromChat(chatId), "Увы, мы ничего не нашли"))
            .map(_ => Done)
        case SapphireService.Vital(result) =>
          InfoScenario(ReplyTo.fromChat(chatId), result, None)
        case SapphireService.Serp(result) =>
          renderSerp(result)
            .flatMap(teleApi.sendMessage)
            .map(_ => Done)
      }
  }
}

object ItemSerializer {
  def from(s: String): SapphireService.Item = {
    s match {
      case msg if msg.startsWith("geo") => SapphireService.Geo(msg.substring(3).toLong)
      case msg if msg.startsWith("branch") => SapphireService.Branch(msg.substring(6).toLong)
    }
  }

  def to(item: SapphireService.Item): String = {
    item match {
      case SapphireService.Geo(id) => s"geo$id"
      case SapphireService.Branch(id) => s"branch$id"
    }
  }
}

object GeometryReader {
  private val GeographicProjectionId = 4326
  private val f = new GeometryFactory(new PrecisionModel(10000000), GeographicProjectionId)

  def apply(in: String): Option[(Double, Double)] = {
    Try(new WKTReader(f).read(in))
      .map {
        _.asInstanceOf[Point]
      }
      .map { p =>
        (p.getCoordinate.y, p.getCoordinate.x)
      }.toOption
  }

}