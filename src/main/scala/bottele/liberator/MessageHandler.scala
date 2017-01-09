package bottele.liberator

import bottele.{City, TelegramBotAPI}
import bottele.TelegramBotAPI.{Message, ReplyTo, SendMessage, Update, UserId}
import bottele.services.SapphireService
import bottele.services.WebAPI.{BranchPayload, BranchSearchPayload, GeoGetPayload, RegionInfoPayload}
import cats._
import cats.data.{Coproduct, EitherT}
import cats.implicits._
import io.aecor.liberator.macros.free
import ru.dgis.sapphire.domain.SearchResult

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@free
trait MessageHandler[F[_]] {
  def handleUpdate(update: Update): F[Unit]
}

object MessageHandlerImpl {
  def apply[F[_] :Monad :Scenarios :Reply](): MessageHandler[F] = new MessageHandler[F] {
    override def handleUpdate(update: Update): F[Unit] = {
      Scenarios[F]
        .showCurrentCity(update.message.get.from.get.id)
        .flatMap { msgs =>
          msgs.map(Reply[F].text(ReplyTo.fromUser(update.message.get.from.get.id), _)).sequence
        }
        .map(_ => Unit)
    }
  }

}



@free
trait Scenarios[F[_]] {
  def citySelection(user: UserId, text: String): F[List[String]]
  def showCurrentCity(user: UserId): F[List[String]]
}

object ScenariosImpl {
  def apply[F[_] :Monad :WebAPI :Sapphire :UserStorage]() = new Scenarios[F] {
    override def showCurrentCity(user: UserId): F[List[String]] = {
      EitherT
        .right[F, List[String], Option[City]](UserStorage[F].getCity(user))
        .subflatMap {
          case None => Left(List("Вы еще не выбрали город"))
          case Some(city) => Right(city.id)
        }
        .semiflatMap(WebAPI[F].regionGetOne)
        .subflatMap {
          case RegionInfoPayload(region :: Nil) => Right(List(s"Ваш текущий город ${region.name}"))
          case RegionInfoPayload(Nil) => Left(List("Не удалось найти ваш город"))
        }
        .valueOr(identity)
    }

    override def citySelection(user: UserId, text: String): F[List[String]] = {
      EitherT
        .pure[F, List[String], String](text)
        .ensure(List("Введите не менее двух симолов"))(_.length > 2)
        .semiflatMap(WebAPI[F].regionSearch)
        .ensure(List("Увы, мы ничего не нашли"))(x => x.items.nonEmpty)
        .subflatMap { regions =>
          if(regions.items.length == 1) {
            Right(regions.items.head)
          } else {
            Left(List(s"Мы нашли такие города: ${regions.items.map(_.name).mkString(", ")}"))
          }
        }
        .semiflatMap { region =>
          UserStorage[F].setCity(user, City(region.id.toInt)).map { _ =>
            List(s"Ваш новый город ${region.name}")
          }
        }
        .valueOr(identity)
    }
  }
}

@free
trait WebAPI[F[_]] {
  def branchGet(ids: Seq[Long]): F[BranchPayload]
  def branchSearch(q: String, city: City): F[BranchSearchPayload]
  def getGet(ids: Seq[Long]): F[GeoGetPayload]
  def regionSearch(q: String): F[RegionInfoPayload]
  def regionGet(ids: Seq[Int]): F[RegionInfoPayload]
  def regionGetOne(id: Int): F[RegionInfoPayload]
}

class WebApiImpl(api: bottele.services.WebAPI, ec: ExecutionContext) extends WebAPI[Future] {
  private implicit val ecc = ec
  override def branchGet(ids: Seq[Long]): Future[BranchPayload] = api.branchGet(ids)
  override def branchSearch(q: String, city: City): Future[BranchSearchPayload] = api.searchBranches(q, city.id)
  override def getGet(ids: Seq[Long]): Future[GeoGetPayload] = api.geoGet(ids)
  override def regionSearch(q: String): Future[RegionInfoPayload] = api.regionSearch(q)
  override def regionGet(ids: Seq[Int]): Future[RegionInfoPayload] = api.regionGet(ids)
  override def regionGetOne(id: Int): Future[RegionInfoPayload] = regionGet(Seq(id))
}

@free
trait Sapphire[F[_]] {
  def search(text: String, city: City): F[SearchResult]
}

class SapphireImpl(api: SapphireService) extends Sapphire[Future] {
  override def search(text: String, city: City): Future[SearchResult] = search(text, city)
}

@free
trait UserStorage[F[_]] {
  def getCity(user: UserId): F[Option[City]]
  def setCity(user: UserId, city: City): F[Unit]
}

class UserStorageImpl extends UserStorage[Future] {
  override def getCity(user: UserId): Future[Option[City]] = Future.successful(Option(City(1)))
  override def setCity(user: UserId, city: City): Future[Unit] = Future.successful(())
}

@free
trait Reply[F[_]] {
  def text(to: ReplyTo, text: String): F[Message]
}

class ReplyImpl(api: TelegramBotAPI, ec: ExecutionContext) extends Reply[Future] {
  private implicit val ecc = ec
  override def text(to: ReplyTo, text: String): Future[Message] = api.sendMessage(SendMessage(to, text, None))
}