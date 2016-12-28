package bottele.free

import bottele.City
import bottele.TelegramBotAPI.UserId
import cats.free.Free


object UserStorage {
  type Algebra[T] = Free[UserStorageFree, T]

  sealed trait UserStorageFree[T]
  final case class GetCity(user: UserId) extends UserStorageFree[Option[City]]
  final case class SetCity(user: UserId, city: City) extends UserStorageFree[Unit]

  def getCity(user: UserId): Algebra[Option[City]] = Free.liftF(GetCity(user))
  def setCity(user: UserId, city: City): Algebra[Unit] = Free.liftF(SetCity(user, city))
}
