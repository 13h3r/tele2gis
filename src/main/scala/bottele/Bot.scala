package bottele

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink}
import bottele.TelegramBotAPI._
import bottele.WebAPI.ApiError
import bottele.scenarios.Router

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Bot extends App {
  implicit val as = ActorSystem()
  implicit val ec = as.dispatcher
  implicit val m = ActorMaterializer(ActorMaterializerSettings(as))

  implicit val webApi = WebAPI(as)
  implicit val teleApi = TelegramBotAPI(as.settings.config.getString("telegram.token"))

  val (control, result) = UpdatesSource(teleApi)
    .map { x => println(s"Got update $x"); x }
    .mapAsync(10) { msg =>
      val chatId = msg.message.map(_.chat.id)
        .orElse(msg.callbackQuery.flatMap(_.message.map(_.chat.id)))
      chatId.map { chatId =>
        teleApi.sendChatActionTyping(chatId).map(_ => msg)
      }.getOrElse(Future.successful(msg))
    }
    .mapAsync(10) { update => Router.apply(update))
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


