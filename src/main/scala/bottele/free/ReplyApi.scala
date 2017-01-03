package bottele.free

import bottele.TelegramBotAPI
import bottele.TelegramBotAPI.{ReplyTo, SendMessage}
import cats.free.{Free, Inject}
import cats.~>

import scala.concurrent.{ExecutionContext, Future}


object Reply {
  sealed trait ReplyFree[T]
  def apply[F[_]](implicit i: Inject[Reply.ReplyFree, F]) = new Reply[F]
  case class Text(to: ReplyTo, text: String) extends ReplyFree[Finish]
}
class Reply[F[_]](implicit i: Inject[Reply.ReplyFree, F]) {
  def text(to: ReplyTo, text: String): Free[F, Finish] = Free.inject(Reply.Text(to, text))
}

object ReplyInterpreter {
  import Reply._
  import cats.instances.future._
  def apply(api: TelegramBotAPI)(implicit ec: ExecutionContext): ReplyFree ~> Future = {
    Lambda[ReplyFree ~> Future] {
      case Text(to, text) => api.sendMessage(new SendMessage(to, text)).map(_ => Finish)
    }
  }
}
