package bottele.free

import bottele.City
import bottele.TelegramBotAPI.{CallbackId, ReplyTo, UserId}
import bottele.free.Reply.ReplyFree
import bottele.free.Sapphire.SapphireFree
import bottele.free.Scenario.{CitySelection, ScenarioFree, Search, ShowCard, ShowCurrentCity}
import bottele.free.UserStorage.UserStorageFree
import bottele.free.WebAPI.WebAPIFree
import bottele.services.SapphireService
import bottele.services.WebAPI.RegionInfoPayload
import cats.data.{Coproduct, EitherT}
import cats.free.{Free, Inject}
import cats.~>

import scala.concurrent.Future
import scala.language.higherKinds

sealed trait Finish
object Finish extends Finish


object Scenario {
  import cats.free._
  type Algebra[T] = Free[ScenarioFree, T]

  sealed trait ScenarioFree[T]

  final case class CitySelection(text: String, reply: ReplyTo, userId: UserId) extends ScenarioFree[Finish]
  final case class ShowCurrentCity(reply: ReplyTo, userId: UserId) extends ScenarioFree[Finish]
  final case class ShowCard(
    reply: ReplyTo,
    obj: SapphireService.Item,
    callbackId: Option[CallbackId]
  ) extends ScenarioFree[Finish]
  final case class Search(text: String, reply: ReplyTo, userId: UserId) extends ScenarioFree[Finish]
  case object UnexpectedScenario extends ScenarioFree[Finish]

  def citySelection(text: String, reply: ReplyTo, userId: UserId): Algebra[Finish] = {
    Free.liftF(CitySelection(text, reply, userId))
  }
  def showCurrentCity(reply: ReplyTo, userId: UserId): Algebra[Finish] = {
    Free.liftF(ShowCurrentCity(reply, userId))
  }

  def showCard(reply: ReplyTo, obj: SapphireService.Item, callbackId: Option[CallbackId]): Algebra[Finish] = {
    Free.liftF(ShowCard(reply, obj, callbackId))
  }
  def search(text: String, reply: ReplyTo, userId: UserId): Algebra[Finish] = {
    Free.liftF(Search(text, reply, userId))
  }

  val unexpectedScenario: Algebra[Finish] = Free.liftF(UnexpectedScenario)
}

object ExternalScenarioInterpreter {
  def interpreter[F[_]](
    implicit u: Inject[UserStorageFree, F],
    s: Inject[SapphireFree, F],
    w: Inject[WebAPIFree, F],
    r: Inject[ReplyFree, F]
  ) = new (Scenario.ScenarioFree ~> Free[F, ?]) {
    import cats.free._
    override def apply[A](fa: ScenarioFree[A]): Free[F, A] = fa match {
      case ShowCurrentCity(reply, user) =>
        EitherT
          .right[Free[F, ?], String, Option[City]](UserStorage[F].getCity(user))
          .subflatMap {
            case None => Left("Вы еще не выбрали город")
            case Some(city) => Right(city.id)
          }
          .semiflatMap(WebAPI[F].regionGetOne)
          .subflatMap {
            case RegionInfoPayload(region :: Nil) => Right(s"Ваш текущий город ${region.name}")
            case RegionInfoPayload(Nil) => Left("Не удалось найти ваш город")
          }
          .valueOr(identity)
          .flatMap(Reply[F].text(reply, _))

      case CitySelection(text, reply, user) =>
        EitherT
          .pure[Free[F, ?], String, String](text)
          .ensure("Введите не менее двух симолов")(_.length > 2)
          .semiflatMap(WebAPI[F].regionSearch)
          .ensure("Увы, мы ничего не нашли")(x => x.items.nonEmpty)
          .subflatMap { regions =>
            if(regions.items.length == 1) {
              Right(regions.items.head)
            } else {
              Left(s"Мы нашли такие города: ${regions.items.map(_.name).mkString(", ")}")
            }
          }
          .semiflatMap { region =>
            UserStorage[F].setCity(user, City(region.id.toInt)).map { _ =>
              s"Ваш новый город ${region.name}"
            }
          }
          .fold(identity, identity)
          .flatMap(Reply[F].text(reply, _))

      //      case Search(t, reply, user) => Reply[ReplyFree].text(reply, t)
//      case ShowCard(reply, user, _) => Reply[ReplyFree].text(reply, reply.toString)
    }
  }
}

object Test {
  type Algebra[T] = Coproduct[Coproduct[Coproduct[
    UserStorageFree,
    SapphireFree, ?],
    WebAPIFree, ?],
    ReplyFree, T]

  val webApi: WebAPIFree ~> Future = ???
  val sapphire: SapphireFree ~> Future = ???
  val userStorage: UserStorageFree ~> Future = ???
  val reply: ReplyFree ~> Future = ???

  val finalInterpreter: Algebra ~> Future = userStorage.or(sapphire).or(webApi).or(reply)
}

object ToStringInterpreter extends (Scenario.ScenarioFree ~> Free[ReplyFree, ?]) {
  import cats.free._
  override def apply[A](fa: ScenarioFree[A]): Free[ReplyFree, A] = fa match {
    case ShowCurrentCity(reply, user) => Reply[ReplyFree].text(reply, reply.toString)
    case CitySelection(_, reply, user) => Reply[ReplyFree].text(reply, reply.toString)
    case Search(t, reply, user) => Reply[ReplyFree].text(reply, t)
    case ShowCard(reply, user, _) => Reply[ReplyFree].text(reply, reply.toString)
  }
}