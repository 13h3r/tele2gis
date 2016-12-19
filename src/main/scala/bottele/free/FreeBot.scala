package bottele.free

import bottele.TelegramBotAPI
import bottele.TelegramBotAPI.Update

import scala.concurrent.{ExecutionContext, Future}

object FreeBot {
  def apply(teleApi: TelegramBotAPI)(implicit ec: ExecutionContext): (Update) => Future[Finish] = {
    import cats.instances.future._
    val interpreter = IncomingMessageInterpreter.instance
      .andThen(ToStringInterpreter)
      .andThen(ReplyInterpreter(teleApi))

    IncomingMessageAlgebra.telegramUpdate(_).foldMap(interpreter)
  }
}