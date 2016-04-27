package bottele

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink}
import bottele.TelegramBotAPI.{Chat, Message, Update}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Bot extends App {
  implicit val as = ActorSystem()
  implicit val ec = as.dispatcher
  implicit val m = ActorMaterializer(ActorMaterializerSettings(as))

  val webApi = WebAPI(as)
  val teleApi = TelegramBotAPI(as.settings.config.getString("telegram.token"))

  val (control, result) = UpdatesSource(teleApi)
    .map { x => println(s"Got update $x"); x }
    .collect {
      case Update(_, Some(Message(_, _, _, Chat(id), Some(text)))) => (id, text)
    }
    .mapAsync(1) { case in@(chatId, text) =>
      teleApi.sendChatActionTyping(chatId).map(_ => in)
    }
    .mapAsync(1) { case (chatId, text) =>
      webApi
        .searchBranches(text, 1, page = Some(1), pageSize = Some(5))
        .map { search =>
          Thread.sleep(2000)
          if(search.items.isEmpty) {
            "Ничего не нашлось :("
          } else {
            search.items.map { branch =>
              val address = branch.address_name.map(address => s" ($address)").getOrElse("")
              s"${branch.name}$address"
            }.mkString("\n")
          }
        }
        .map(reply => (chatId, Some(reply)))
        .fallbackTo(Future.successful((chatId, None)))
    }
    .mapAsync(1) {
      case (chatId, Some(reply)) => teleApi.sendMessage(chatId, reply)
      case (chatId, None) => teleApi.sendMessage(chatId, "У нас что-то пошло не так :(")
    }
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
}
