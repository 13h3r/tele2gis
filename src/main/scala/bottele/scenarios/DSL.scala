package bottele.scenarios

import bottele.TelegramBotAPI.{CallbackQuery, Chat, ChatId, Message, Update, User, UserId}

import scala.concurrent.Future

trait Dsl {
  type NoMatches = Unit
  type URouteResult = Either[NoMatches, Future[Any]]
  type URoute = Update => URouteResult

  implicit class RouteOps(a: URoute) {
    def ~(b: URoute): URoute = u => a(u) match {
      case r@Right(_) => r
      case Left(_) => b(u)
    }
  }

  val reject: URouteResult = Left(())

  def result[T](t: T): URoute = _ => Right(Future.successful(t))
  def asyncResult[T](t: Future[T]): URoute = _ => Right(t)

  abstract class Directive[T]() {
    def apply(inner: T => URoute): URoute

    def filter(predicate: T => Boolean): Directive[T] = {
      Directive[T](inner =>
        apply(t => u => {
          if(predicate(t)) inner(t)(u)
          else reject
        })
      )
    }

    def map[U](f: T => U): Directive[U] = {
      Directive[U](inner => u => apply(t => inner(f(t)))(u))
    }

    def collect[U](pf: PartialFunction[T, U]): Directive[U] = {
      filter(pf.isDefinedAt).map(pf)
    }
  }

  object Directive {
    def apply[T](f: (T => URoute) => URoute): Directive[T] = new Directive[T] {
      override def apply(inner: (T) => URoute): URoute = f(inner)
    }
  }

  val updateRequest: Directive[Update] = Directive[Update] { inner => u => inner(u)(u) }
  val empty: Directive[Unit] = Directive { inner => inner(()) }
  def filter(f: => Boolean): Directive[Unit] = empty.filter(_ => f)
}

trait UpdateDirectives extends Dsl {

  val message: Directive[Message] = updateRequest.collect {
    case u if u.message.isDefined => u.message.get
  }

  val messageUserId: Directive[UserId] = message.collect {
    case m if m.from.isDefined => m.from.get.id
  }
  val messageChatId: Directive[ChatId] = message.map(_.chat.id)
  val messageText: Directive[String] = message.map(_.text).collect {
    case Some(text) => text
  }

  def command(name: String): Directive[String] = {
    val prefix = "/" + name
    messageText.filter(_.startsWith(prefix)).map(_.substring(prefix.length).trim)
  }

  def emptyCommand(name: String): Directive[Unit] = command(name).filter(_.isEmpty).map(_ => ())
  def nonEmptyCommand(name: String): Directive[String] = command(name).filter(!_.isEmpty)

  val callback: Directive[CallbackQuery] = updateRequest.collect {
    case u if u.callbackQuery.isDefined => u.callbackQuery.get
  }
}

trait Telerouting
  extends UpdateDirectives
object Telerouting extends Telerouting
