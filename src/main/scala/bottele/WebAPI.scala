package bottele

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import akka.util.{ByteString, Timeout}
import bottele.WebAPI.WebAPIProtocol
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}


object WebAPI {
  def apply(as: ActorSystem) = new WebAPI {
    val actorSystem = as
  }
  case class Response[T](meta: Meta, result: Option[T]) {
    def toEither: Either[ApiError, T] = this match {
      case Response(Meta(200, _), Some(_result)) => Right(_result)
      case Response(Meta(code, Some(error)), _) => Left(ApiError(code, error.`type`, error.message))
    }
  }

  case class ApiError(code: Int, `type`: String, message: String) extends Exception(s"Api error. Code $code, type ${`type`}, message $message")
  case class Meta(code: Int, error: Option[MetaError])
  case class MetaError(`type`: String, message: String)

  case class Branch(id: String, name: String, address_name: Option[String])
  case class BranchPayload(items: Seq[Branch])
  case class Search(total: Long, items: Seq[Branch])

  trait WebAPIProtocol extends DefaultJsonProtocol {
    implicit val branchFormat = jsonFormat3(Branch)
    implicit val branchPayloadFormat = jsonFormat1(BranchPayload)
    implicit val metaErrorFormat = jsonFormat2(MetaError)
    implicit val metaFormat = jsonFormat2(Meta)
    implicit val searchFormat = jsonFormat2(Search)
    implicit val branchResponseFormat: RootJsonFormat[Response[BranchPayload]] = jsonFormat2(Response[BranchPayload])
    implicit val searchResponseFormat: RootJsonFormat[Response[Search]] = jsonFormat2(Response[Search])
  }
}

trait WebAPI {
  private object JsonFormats extends WebAPIProtocol
  import JsonFormats._
  import WebAPI._
  protected implicit def actorSystem: ActorSystem
  private val config = ConfigFactory.load()
  private val key = config.getString("web-api.key")
  private val host = config.getString("web-api.host")
  protected lazy val http = Http(actorSystem)
  protected lazy implicit val m: Materializer = ActorMaterializer(ActorMaterializerSettings(actorSystem))

  private def optionToMap[T](key: String, value: Option[T]) = {
    value.map(x => Map(key -> x.toString)).getOrElse(Map.empty)
  }

  def branches(ids: Seq[Long])(implicit ec: ExecutionContext) = {
    execute[BranchPayload](
      Uri(s"http://$host/2.0/catalog/branch/get").withQuery(Query(
        "id" -> ids.mkString(","),
        "key" -> key,
        "format" -> "json"
      ))
    )
  }

  def searchBranches(q: String, project: Int, page: Option[Int] = None, pageSize: Option[Int] = None)
                    (implicit ec: ExecutionContext) =
  {
    execute[Search](
      Uri(s"http://$host/2.0/catalog/branch/search").withQuery(Query(Map(
        "key" -> key,
        "format" -> "json",
        "q" -> q,
        "region_id" -> project.toString
      ) ++ optionToMap("page", page) ++ optionToMap("page_size", pageSize)
    ))).map {
      case Left(ApiError(404, _, _)) => Search(0, Seq.empty)
      case Left(ex) => throw ex
      case Right(result) => result
    }

  }

  private def execute[T](uri: Uri)(implicit
                                   ec: ExecutionContext,
                                   converter: JsonFormat[Response[T]]
  ): Future[Either[ApiError, T]] =
  {
    import spray.json._
    http.singleRequest(HttpRequest(uri = uri))
      .flatMap { resp =>
        if(resp.status.isSuccess) resp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
        else Future.failed(new Exception(s"Got ${resp.status.intValue} HTTP code from API during request - ${uri.toString()}"))
      }
      .map { _.utf8String.parseJson.asJsObject().convertTo[Response[T]].toEither }
  }
}

