package bottele.liberator

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import bottele.{TelegramBotAPI, UpdatesSource}
import cats.free.Free
import cats.~>
import io.aecor.liberator.{FreeAlgebra, ProductKK}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by alexey on 03/01/2017.
  */
object LiberatedBot extends App {
  import cats.instances.future._
  implicit val as = ActorSystem()
  implicit val ec = as.dispatcher
  implicit val m = ActorMaterializer(ActorMaterializerSettings(as))

//  implicit val webApi = WebAPI(as)
  implicit val teleApi = TelegramBotAPI(as.settings.config.getString("telegram.token"))

  val algebra = FreeAlgebra[ProductKK[Scenarios, Reply, ?[_]]]

  import Scenarios._
  val sc: Scenarios.ScenariosFree ~> Future = ???
  val r: Reply[Future] = ???
  val interpreter = algebra(ProductKK(Scenarios.fromFunctionK(sc), r))

  val handler = MessageHandlerImpl.apply[Free[algebra.Out, ?]]()
  val (control, result) = UpdatesSource(teleApi)
    .map { x => println(s"Got update $x"); x }
    .mapAsync(10) { msg =>
      handler.handleUpdate(msg).foldMap(interpreter)
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

