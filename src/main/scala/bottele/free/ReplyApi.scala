package bottele.free

import bottele.TelegramBotAPI
import bottele.TelegramBotAPI.{ReplyTo, SendMessage}
import cats.~>

import scala.concurrent.{ExecutionContext, Future}


object Reply {
  import cats.free._
  sealed trait ReplyFree[T]

  case class Text(to: ReplyTo, text: String) extends ReplyFree[Finish]

  type ReplyA[T] = Free[ReplyFree, T]

  def text(to: ReplyTo, text: String): Free[ReplyFree, Finish] = Free.liftF(Text(to, text))
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
