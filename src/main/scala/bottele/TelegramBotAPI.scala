package bottele

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import akka.util.ByteString
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, JsonReader, JsonWriter}

import scala.collection.immutable.{Map, Seq}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object TelegramBotAPI {
  def apply(botKey: String)(implicit as: ActorSystem) = new TelegramBotAPI {
    override protected val key: String = botKey
    override protected implicit val m: Materializer = ActorMaterializer(ActorMaterializerSettings(as))
    override protected val http: HttpExt = Http(as)
  }

  case class UserId(id: Long) extends AnyVal
  case class ChatId(id: Long) extends AnyVal
  case class MessageId(id: Long) extends AnyVal
  case class UpdateId(id: Long) extends AnyVal
  case class CallbackId(id: String) extends AnyVal

  case class ServerResponse(ok: Boolean, result: JsValue)
  case class User(id: UserId, firstName: String, lastName: Option[String], username: Option[String])
  case class Chat(id: ChatId)
  case class SendMessage(chatId: Either[ChatId, UserId], text: String, replyMarkup: Option[ReplyMarkup] = None)
  case class SendLocation(chatId: Either[ChatId, UserId], latitude: Double, longitude: Double)
  case class Message(messageId: MessageId, from: Option[User], date: Long, chat: Chat, text: Option[String])
  case class Update(updateId: UpdateId, message: Option[Message], chosenInlineResult: Option[ChosenInlineResult], callbackQuery: Option[CallbackQuery])

  trait ReplyMarkup
  case class InlineKeyboardButton(text: String, url: Option[String], callbackData: Option[String])
  case class InlineKeyboardMarkup(inlineKeyboard: List[List[InlineKeyboardButton]]) extends ReplyMarkup
  case class ChosenInlineResult(resultId: String, from: User, inlineMessageId: Option[String], query: String)

  case class CallbackQuery(id: CallbackId, from: User, message: Option[Message], inlineMessageId: Option[String], data: String)
  //
  case class ApiException(text: String, req: HttpRequest) extends Exception(s"$text\n$req\n")

  trait JsonProtocol extends DefaultJsonProtocol with SnakifiedSprayJsonSupport {

    def wrapper[Result, Inner](to: Inner => Result, from: Result => Inner)(implicit f: JsonFormat[Inner]): JsonFormat[Result] = new JsonFormat[Result] {
      override def write(obj: Result): JsValue = {
        f.write(from(obj))
      }
      override def read(json: JsValue): Result = {
        to(f.read(json))
      }
    }

    implicit val json_messageId = wrapper[MessageId, Long](MessageId.apply, _.id)
    implicit val json_userId = wrapper[UserId, Long](UserId.apply, _.id)
    implicit val json_chatId = wrapper[ChatId, Long](ChatId.apply, _.id)
    implicit val json_updateId = wrapper[UpdateId, Long](UpdateId.apply, _.id)
    implicit val json_callbackId = wrapper[CallbackId, String](CallbackId.apply, _.id)

    implicit val json_serverResponse = jsonFormat2(ServerResponse)
    implicit val json_chat = jsonFormat1(Chat)
    implicit val json_user = jsonFormat4(User)
    implicit val json_chosenInlineResult = jsonFormat4(ChosenInlineResult)
    implicit val json_replyMarkup = {
      implicit val json_inline_button = jsonFormat3(InlineKeyboardButton)
      implicit val json_inline = jsonFormat1(InlineKeyboardMarkup)
      jsonFormat[ReplyMarkup](
        new JsonReader[ReplyMarkup] {
          override def read(json: JsValue): ReplyMarkup = ???
        },
        new JsonWriter[ReplyMarkup] {
          override def write(obj: ReplyMarkup): JsValue = obj match {
            case m: InlineKeyboardMarkup => json_inline.write(m)
          }
        }
      )
    }
    implicit val json_message = jsonFormat5(Message)
    implicit val json_callbackQuery= jsonFormat5(CallbackQuery)
    implicit val json_sendMessage = jsonFormat3(SendMessage)
    implicit val json_sendLocation = jsonFormat3(SendLocation)
    implicit val json_update = jsonFormat4(Update)
  }
  object JsonProtocol extends TelegramBotAPI.JsonProtocol
}

trait TelegramBotAPI {
  import TelegramBotAPI._
  import JsonProtocol._
  import spray.json._

  protected val http: HttpExt
  protected implicit val m: Materializer
  protected val key: String

  lazy private val basePath = s"https://api.telegram.org/bot$key"
  private def optionToMap[T](key: String, value: Option[T]) = {
    value.map(x => Map(key -> x.toString)).getOrElse(Map.empty)
  }

  def sendChatActionTyping(chatId: ChatId)(implicit ec: ExecutionContext) = {
    val uri = Uri(basePath + "/sendChatAction").withQuery(Query(
      "chat_id" -> chatId.id.toString,
      "action" -> "typing"
    ))
    execute[JsValue](HttpRequest(uri = uri))
  }

  def sendMessage(msg: SendMessage)(implicit ec: ExecutionContext): Future[Message] = {
    val uri = Uri(basePath + "/sendMessage")
    execute[Message](HttpRequest(uri = uri).withEntity(
      HttpEntity(ContentTypes.`application/json`, msg.toJson.prettyPrint)
    ))
  }

  def sendLocation(msg: SendLocation)(implicit ec: ExecutionContext): Future[Message] = {
    val uri = Uri(basePath + "/sendLocation")
    execute[Message](HttpRequest(uri = uri).withEntity(
      HttpEntity(ContentTypes.`application/json`, msg.toJson.prettyPrint)
    ))
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

  def answerCallbackQuery(callbackQueryId: CallbackId, text: Option[String] = None)(implicit ec: ExecutionContext): Future[Unit] = {
    val uri = Uri(basePath + "/answerCallbackQuery").withQuery(Query(
      Map("callback_query_id" -> callbackQueryId.id) ++
        optionToMap("text", text)
    ))
    executeHTTP(HttpRequest(uri = uri)).map(_ => ())
  }

  private def executeHTTP(request: HttpRequest)(implicit ec: ExecutionContext) = {
    http
      .singleRequest(request)
      .flatMap { resp =>
        val body = resp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
        if(resp.status.isSuccess()) {
          if(resp.entity.contentType == ContentTypes.`application/json`) {
            body
          } else Future.failed(ApiException(s"Wrong content type - ${resp.entity.contentType}", request))
        } else {
          val descr = Await.result(body, 1.seconds).utf8String
          Future.failed(ApiException(s"Wrong http code - ${resp.status}\n${descr}", request))
        }
      }
  }
  private def execute[T : JsonFormat](request: HttpRequest)(implicit ec: ExecutionContext) = {
    println(request)
    executeHTTP(request)
      .flatMap { bytes =>
        import spray.json._
        val response = bytes.utf8String.parseJson.convertTo[ServerResponse]
        if(response.ok) Future.successful(response.result)
        else Future.failed(ApiException(s"Server returned not ok - ${response.toJson.prettyPrint}", request))
      }
      .map(_.convertTo[T])
  }
}