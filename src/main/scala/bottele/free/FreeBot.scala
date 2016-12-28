package bottele.free

import bottele.TelegramBotAPI
import bottele.TelegramBotAPI.Update
import cats.~>

import scala.concurrent.{ExecutionContext, Future}

object FreeBot {
  def apply(teleApi: TelegramBotAPI)(implicit ec: ExecutionContext): (Update) => Future[Finish] = {
    import cats.instances.future._

    val i = new (IncomingMessage.IncomingMessageFree ~> Future) {
      override def apply[A](fa: IncomingMessage.IncomingMessageFree[A]): Future[A] = {
        IncomingMessageInterpreter.instance.apply(fa)
          .foldMap(ToStringInterpreter)
          .foldMap(ReplyInterpreter(teleApi))
      }
    }
    (u: Update) => IncomingMessage.telegramUpdate(u).foldMap(i)
  }
}