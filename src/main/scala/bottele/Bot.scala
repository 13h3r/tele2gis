package bottele

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink}
import bottele.scenarios.Router
import bottele.userstorage.NaiveUserStorage

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Bot extends App {
  implicit val as = ActorSystem()
  implicit val ec = as.dispatcher
  implicit val m = ActorMaterializer(ActorMaterializerSettings(as))

  implicit val webApi = WebAPI(as)
  implicit val teleApi = TelegramBotAPI(as.settings.config.getString("telegram.token"))
  implicit val storage = new NaiveUserStorage(as.settings.config.getString("naive-storage.conn"))

  val (control, result) = UpdatesSource(teleApi)
    .map { x => println(s"Got update $x"); x }
    .mapAsync(10) { msg =>
      val chatId = msg.message.map(_.chat.id)
        .orElse(msg.callbackQuery.flatMap(_.message.map(_.chat.id)))
      chatId.map { chatId =>
        teleApi.sendChatActionTyping(chatId).map(_ => msg)
      }.getOrElse(Future.successful(msg))
    }
    .mapAsync(10) { update => Router.apply(update) }
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


