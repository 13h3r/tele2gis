package bottele.free

import bottele.City
import bottele.TelegramBotAPI.UserId
import cats.free._


object UserStorage {
  def apply[F[_]](implicit i: Inject[UserStorageFree, F]) = new UserStorage[F]

  sealed trait UserStorageFree[T]
  final case class GetCity(user: UserId) extends UserStorageFree[Option[City]]
  final case class SetCity(user: UserId, city: City) extends UserStorageFree[Unit]
}
class UserStorage[F[_]](implicit i: Inject[UserStorage.UserStorageFree, F]) {
  import UserStorage._
  def getCity(user: UserId): Free[F, Option[City]] = Free.inject(GetCity(user))
  def setCity(user: UserId, city: City): Free[F, Unit] = Free.inject(SetCity(user, city))
}
