package bottele.free

import bottele.TelegramBotAPI
import bottele.TelegramBotAPI.{ReplyTo, SendMessage}
import cats.~>

import scala.concurrent.{ExecutionContext, Future}


sealed trait Reply[T]

case class Text(to: ReplyTo, text: String) extends Reply[Finish]

object ReplyAlgebra {
  import cats.free._
  def text(to: ReplyTo, text: String): Free[Reply, Finish] = Free.liftF(Text(to, text))
}

object ReplyInterpreter {
  import cats.instances.future._
  def apply(api: TelegramBotAPI)(implicit ec: ExecutionContext): Reply ~> Future = {
    Lambda[Reply ~> Future] {
      case Text(to, text) => api.sendMessage(new SendMessage(to, text)).map(_ => Finish)
    }
  }
}
