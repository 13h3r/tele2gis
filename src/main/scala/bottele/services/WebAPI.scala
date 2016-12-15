package bottele.services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import akka.util.ByteString
import bottele.services.WebAPI.WebAPIProtocol
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}


object WebAPI {
  def apply(as: ActorSystem) = new WebAPI {
    val actorSystem: ActorSystem = as
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
  case class Point(lon: Double, lat: Double)
  case class Contact(`type`: String, text: String, comment: Option[String])
  case class ContactGroups(contacts: List[Contact])
  case class Branch(id: String, name: String, address_name: Option[String], contact_groups: Option[Seq[ContactGroups]], point: Option[Point])
  case class BranchPayload(items: List[Branch])
  case class Search(total: Long, items: List[Branch])
  case class RegionItem(name: String, id: String, `type`: String)
  case class RegionPayload(items: List[RegionItem])
  case class RegionInfoPayload(items: List[RegionInfo])
  case class RegionInfo(name: String, id: String, `type`: String)

  case class Geometry(centroid: Option[String])
  case class Geo(id: String, name: String, purpose_name: Option[String], full_name: String, `type`: String, geometry: Option[Geometry])
  case class GeoGetPayload(items: List[Geo])

  trait WebAPIProtocol extends DefaultJsonProtocol {
    implicit val contactFormat = jsonFormat3(Contact)
    implicit val pointFormat = jsonFormat2(Point)
    implicit val contactGroupsFormat = jsonFormat1(ContactGroups)
    implicit val branchFormat = jsonFormat5(Branch)
    implicit val branchPayloadFormat = jsonFormat1(BranchPayload)
    implicit val metaErrorFormat = jsonFormat2(MetaError)
    implicit val metaFormat = jsonFormat2(Meta)
    implicit val searchFormat = jsonFormat2(Search)
    implicit val regionFormat = jsonFormat3(RegionItem)
    implicit val regionPayloadFormat = jsonFormat1(RegionPayload)
    implicit val regionInfoFormat = jsonFormat3(RegionInfo)
    implicit val regionInfoPayloadFormat = jsonFormat1(RegionInfoPayload)
    implicit val geometryFormat = jsonFormat1(Geometry)
    implicit val geoItemFormat = jsonFormat6(Geo)
    implicit val geoGetPayloadFormat = jsonFormat1(GeoGetPayload)
    implicit val branchResponseFormat: RootJsonFormat[Response[BranchPayload]] = jsonFormat2(Response[BranchPayload])
    implicit val searchResponseFormat: RootJsonFormat[Response[Search]] = jsonFormat2(Response[Search])
    implicit val regionResponseFormat: RootJsonFormat[Response[RegionPayload]] = jsonFormat2(Response[RegionPayload])
    implicit val regionInfoResponseFormat: RootJsonFormat[Response[RegionInfoPayload]] = jsonFormat2(Response[RegionInfoPayload])
    implicit val getGetResponseFormat: RootJsonFormat[Response[GeoGetPayload]] = jsonFormat2(Response[GeoGetPayload])
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

  def branchGet(ids: Seq[Long])(implicit ec: ExecutionContext): Future[BranchPayload] = {
    execute[BranchPayload](
      Uri(s"http://$host/2.0/catalog/branch/get").withQuery(Query(
        "id" -> ids.mkString(","),
        "key" -> key,
        "format" -> "json",
        "fields" -> "items.point"
      ))
    ).map {
      case Left(ApiError(404, _, _)) => BranchPayload(List.empty)
      case Left(ex) => throw ex
      case Right(result) => result
    }
  }

  def branchGetOne(id: Long)(implicit ec: ExecutionContext): Future[Option[Branch]] = {
    branchGet(Seq(id)).map(_.items.headOption)
  }

  def regionSearch(q: String)(implicit ec: ExecutionContext): Future[RegionInfoPayload] = {
    execute[RegionInfoPayload](
      Uri(s"http://$host/2.0/region/search").withQuery(Query(
        "key" -> key,
        "q" -> q
      ))
    ).map {
      case Left(ApiError(404, _, _)) => RegionInfoPayload(List.empty)
      case Left(ex) => throw ex
      case Right(result) => result
    }
  }

  def geoGet(ids: Seq[Long])(implicit ec: ExecutionContext): Future[GeoGetPayload] = {
    execute[GeoGetPayload](
      Uri(s"http://$host/2.0/geo/get").withQuery(Query(
        "key" -> key,
        "id" -> ids.mkString(","),
        "fields" -> "items.geometry.centroid"
      ))
    ).map {
      case Left(ApiError(404, _, _)) => GeoGetPayload(List.empty)
      case Left(ex) => throw ex
      case Right(result) => result
    }
  }

  def geoGetOne(id: Long)(implicit ec: ExecutionContext): Future[Option[Geo]] = {
    geoGet(Seq(id)).map(_.items.headOption)
  }

  def regionGet(id: Int)(implicit ec: ExecutionContext): Future[RegionInfoPayload] = {
    execute[RegionInfoPayload](
      Uri(s"http://$host/2.0/region/get").withQuery(Query(
        "key" -> key,
        "id" -> id.toString
      ))
    ).map {
      case Left(ApiError(404, _, _)) => RegionInfoPayload(List.empty)
      case Left(ex) => throw ex
      case Right(result) => result
    }
  }


  def searchBranches(q: String, project: Int, page: Option[Int] = None, pageSize: Option[Int] = None)
                    (implicit ec: ExecutionContext): Future[Search] = {
    execute[Search](
      Uri(s"http://$host/2.0/catalog/branch/search").withQuery(Query(Map(
        "key" -> key,
        "format" -> "json",
        "q" -> q,
        "region_id" -> project.toString
      ) ++ optionToMap("page", page) ++ optionToMap("page_size", pageSize)
      ))).map {
      case Left(ApiError(404, _, _)) => Search(0, List.empty)
      case Left(ex) => throw ex
      case Right(result) => result
    }

  }

  private def execute[T](uri: Uri)(implicit
                                   ec: ExecutionContext,
                                   converter: JsonFormat[Response[T]]
  ): Future[Either[ApiError, T]] = {
    import spray.json._
    println(uri)
    http.singleRequest(HttpRequest(uri = uri))
      .flatMap { resp =>
        if (resp.status.isSuccess) resp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
        else Future.failed(new Exception(s"Got ${resp.status.intValue} HTTP code from API during request - ${uri.toString()}"))
      }
      .map {
        _.utf8String.parseJson.asJsObject().convertTo[Response[T]].toEither
      }
  }
}

