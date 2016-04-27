package bottele

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpRequest, HttpResponse, Uri}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import akka.util.ByteString
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat}

import scala.collection.immutable.{Map, Seq}
import scala.concurrent.{ExecutionContext, Future}

object TelegramBotAPI {
  def apply(botKey: String)(implicit as: ActorSystem) = new TelegramBotAPI {
    override protected val key: String = botKey
    override protected implicit val m: Materializer = ActorMaterializer(ActorMaterializerSettings(as))
    override protected val http: HttpExt = Http(as)
  }

  case class ServerResponse(ok: Boolean, result: JsValue)
  case class User(id: Long, firstName: String, lastName: Option[String], username: Option[String])
  case class Chat(id: Long)
  case class Message(messageId: Long, from: Option[User], date: Long, chat: Chat, text: Option[String])
  case class Update(updateId: Long, message: Option[Message])
  //
  case class ApiException(text: String, req: HttpRequest) extends Exception(s"$text\n$req\n")

  trait JsonProtocol extends DefaultJsonProtocol with SnakifiedSprayJsonSupport {
    implicit val json_serverResponse = jsonFormat2(ServerResponse)
    implicit val json_chat = jsonFormat1(Chat)
    implicit val json_user = jsonFormat4(User)
    implicit val json_message = jsonFormat5(Message)
    implicit val json_update = jsonFormat2(Update)
  }
  object JsonProtocol extends TelegramBotAPI.JsonProtocol
}

trait TelegramBotAPI {
  import TelegramBotAPI._
  import JsonProtocol._

  protected val http: HttpExt
  protected implicit val m: Materializer
  protected val key: String

  lazy private val basePath = s"https://api.telegram.org/bot$key"
  private def optionToMap[T](key: String, value: Option[T]) = {
    value.map(x => Map(key -> x.toString)).getOrElse(Map.empty)
  }

  def sendChatActionTyping(chatId: Long)(implicit ec: ExecutionContext) = {
    val uri = Uri(basePath + "/sendChatAction").withQuery(Query(
      "chat_id" -> chatId.toString,
      "action" -> "typing"
    ))
    execute[JsValue](HttpRequest(uri = uri))
  }

  def sendMessage(chatId: Long, text: String)(implicit ec: ExecutionContext): Future[Message] = {
    val uri = Uri(basePath + "/sendMessage").withQuery(Query(
      "chat_id" -> chatId.toString,
      "text" -> text
    ))
    execute[Message](HttpRequest(uri = uri))
  }

  def getUpdates(
    offset: Option[Long] = None,
    limit: Option[Long] = None,
    timeout: Option[Long] = None
  )(implicit ec: ExecutionContext): Future[Seq[Update]] =
  {
    val uri = Uri(basePath + "/getUpdates").withQuery(Query(
      optionToMap("offset", offset) ++
        optionToMap("limit", limit) ++
        optionToMap("timeout", timeout))
    )
    execute[Seq[Update]](HttpRequest(uri = uri))
  }

  private def execute[T : JsonFormat](request: HttpRequest)(implicit ec: ExecutionContext) = {
    println(request)
    http
      .singleRequest(request)
      .flatMap { resp =>
        if(resp.status.isSuccess()) {
          if(resp.entity.contentType == ContentTypes.`application/json`) {
            resp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
          } else Future.failed(ApiException(s"Wrong content type - ${resp.entity.contentType}", request))
        } else Future.failed(ApiException(s"Wrong http code - ${resp.status}", request))
      }
      .flatMap { bytes =>
        import spray.json._
        val response = bytes.utf8String.parseJson.convertTo[ServerResponse]
        if(response.ok) Future.successful(response.result)
        else Future.failed(ApiException(s"Server returned not ok - ${response.toJson.prettyPrint}", request))
      }
      .map(_.convertTo[T])
  }
}