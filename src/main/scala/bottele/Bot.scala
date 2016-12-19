package bottele

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink}
import bottele.free.FreeBot
import bottele.services.{NaiveUserStorage, SapphireService, WebAPI}
import ru.dgis.sapphire.SapphireClient

import scala.util.{Failure, Success}

object Bot extends App {
  implicit val as = ActorSystem()
  implicit val ec = as.dispatcher
  implicit val m = ActorMaterializer(ActorMaterializerSettings(as))

  implicit val webApi = WebAPI(as)
  implicit val teleApi = TelegramBotAPI(as.settings.config.getString("telegram.token"))
  implicit val storage = new NaiveUserStorage(
    as.settings.config.getString("naive-storage.conn"),
    as.settings.config.getString("naive-storage.user"),
    as.settings.config.getString("naive-storage.pass")
  )
  implicit val sapphire = SapphireService(SapphireClient("sapphire"))


  val process = FreeBot(teleApi)
  val (control, result) = UpdatesSource(teleApi)
    .map { x => println(s"Got update $x"); x }
    .mapAsync(10) { msg =>
      process(msg)
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


