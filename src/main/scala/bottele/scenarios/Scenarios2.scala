package bottele.scenarios

import bottele.TelegramBotAPI.{CallbackQuery, Chat, Message, Update, User}

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

  def complete[T](t: T): URoute = _ => Right(Future.successful(t))

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

  val update: Directive[Update] = Directive[Update] { inner => u => inner(u)(u) }

}

trait UpdateDirectives extends Dsl {
  val message: Directive[Message] = update.collect {
    case u if u.message.isDefined => u.message.get
  }

  val messageChatId: Directive[Chat] = message.map(_.chat)
  val messageText: Directive[String] = message.map(_.text).collect {
    case Some(text) => text
  }

  val callback: Directive[CallbackQuery] = update.collect {
    case u if u.callbackQuery.isDefined => u.callbackQuery.get
  }
  val callbackFrom: Directive[User] = callback.map(_.from)

}

object RRRR extends App with UpdateDirectives {
import scala.concurrent.ExecutionContext.Implicits.global
  val r = message { msg =>
    complete(s"msg $msg")
  } ~ callback { callback =>
    complete(s"callback $callback")
  }

  def run(result: URouteResult) = result match {
    case Left(_) => println("No matches")
    case Right(f) => f.onComplete(println)
  }

  run(r(Update(1, None, None, None)))
  run(r(Update(1, Some(new Message(1, None, 1L, null, None)), None, None)))
  run(r(Update(1, None, None, Some(CallbackQuery("11", null, None, None, "")))))
}
