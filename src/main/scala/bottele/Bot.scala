package bottele

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink}
import bottele.TelegramBotAPI._
import bottele.WebAPI.ApiError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Bot extends App {
  implicit val as = ActorSystem()
  implicit val ec = as.dispatcher
  implicit val m = ActorMaterializer(ActorMaterializerSettings(as))

  val webApi = WebAPI(as)
  val teleApi = TelegramBotAPI(as.settings.config.getString("telegram.token"))

  val (control, result) = UpdatesSource(teleApi)
    .map { x => println(s"Got update $x"); x }
    .mapAsync(10) { msg =>
      val chatId = msg.message.map(_.chat.id)
        .orElse(msg.callbackQuery.flatMap(_.message.map(_.chat.id)))
      chatId.map { chatId =>
        teleApi.sendChatActionTyping(chatId).map(_ => msg)
      }.getOrElse(Future.successful(msg))
    }
    .mapAsync(10)(Handler.apply)
    .toMat(Sink.ignore)(Keep.both)
    .run()

  result.onComplete {
    case Success(x) =>
      println(x)
      sys.exit(0)
    case Failure(ex) =>
      ex.printStackTrace()
      sys.exit(1)
  }
  object Handler {
    def apply(update: Update)(implicit ec: ExecutionContext): Future[Done] = update match {
      case u if u.message.isDefined && u.message.get.text.isDefined =>
        search(u.message.get.chat.id, u.message.get.text.get)
      case Update(_ ,_ ,_, Some(callback)) =>
        info(callback.from.id, callback.data, callback.id.toLong)
      case _ => Future.successful(Done)
    }

    def info(chatId: Long, filial: String, callbackId: Long): Future[Done] = {
      trait Reply
      case class Location(lon: Double, lat: Double)
      case class Info(name: String, phones: Iterable[String], location: Option[Location]) extends Reply
      object NotFound extends Reply
      object InternalError extends Reply
      webApi
        .branches(Seq(filial.toLong))
        .map { branches =>
          branches match {
            case Left(ApiError(404, _, _)) => NotFound
            case Left(ApiError(_, _, _)) => InternalError
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
          case InternalError => teleApi.sendMessage(SendMessage(chatId, "Что-то пошло не так"))
          case Info(name, phones, location) =>
            val phonesText = Option(phones)
              .filter(_.nonEmpty)
              .map(_.mkString(", "))
              .map(p => s"Позвонить: $p\n")
              .getOrElse("")
            val text = s"$name\n$phonesText"

            for {
              callbackF <- teleApi.answerCallbackQuery(callbackId)
              msgF <- teleApi.sendMessage(SendMessage(chatId, text))
              locatF <- location.map {
                case Location(lon, lat) =>
                  teleApi.sendLocation(SendLocation(chatId, lat, lon))
              }.getOrElse(Future.successful(Done))
            } yield Done
        }
        .map(_ => Done)
    }

    def search(chatId: Long, text: String): Future[Done] = {
      case class Reply(chatId: Long, response: Response)
      trait Response
      object UnknownUpdate extends Response
      object InternalError extends Response
      object NotFound extends Response
      case class Firm(id: String, name: String)
      case class Firms(firms: List[Firm]) extends Response

      def renderReply(reply: Reply) = reply.response match {
        case InternalError => SendMessage(reply.chatId, "Что-то пошло не так")
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
        .fallbackTo(Future.successful(InternalError))
        .map(reply => renderReply(Reply(chatId, reply)))
        .flatMap(teleApi.sendMessage)
        .map(_ => Done)
    }
  }
}


