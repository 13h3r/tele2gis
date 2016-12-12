package bottele

import akka.actor.ActorSystem
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import bottele.TelegramBotAPI.Update

import scala.annotation.tailrec
import scala.collection.immutable.{Queue, Seq}
import scala.util.{Failure, Success, Try}

trait Control {
  def stop(): Unit
}

object UpdatesSource {
  def apply(api: TelegramBotAPI)(implicit as: ActorSystem) = Source.fromGraph(new UpdatesSource(api))
}

class UpdatesSource(api: TelegramBotAPI)(implicit as: ActorSystem) extends GraphStageWithMaterializedValue[SourceShape[Update], Control] {
  val out = Outlet[Update]("messages")
  val shape = new SourceShape(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Control) = {
    val logic = new GraphStageLogic(shape) with Control {
      private var offset: Option[Long] = None
      private var buffer = Queue.empty[Update]

      @tailrec
      def pump(): Unit = {
        if (isAvailable(out)) {
          if (buffer.nonEmpty) {
            val (msg, newBuffer) = buffer.dequeue
            buffer = newBuffer
            push(out, msg)
            pump()
          } else {
            import as.dispatcher
            api
              .getUpdates(offset = offset, timeout = Some(5))
              .onComplete(updatesCallback.invoke)
          }
        }
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pump()
        }
      })

      val updatesCallback = getAsyncCallback[Try[Seq[Update]]] {
        case Success(updates) =>
          offset = updates.lastOption.map(_.updateId.id + 1).orElse(offset)
          buffer = buffer.enqueue(updates)
          pump()
        case Failure(ex) => fail(out, ex)
      }

      val stopCallback = getAsyncCallback[Unit](_ => complete(shape.out))

      override def stop(): Unit = stopCallback.invoke()
    }
    (logic, logic)
  }
}
