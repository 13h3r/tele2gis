package bottele.free

import bottele.City
import bottele.services.WebAPI.{BranchPayload, GeoGetPayload, RegionInfoPayload}
import cats.free.{Free, Inject}


object WebAPI {
  trait WebAPIFree[T]

  case class BranchGet(ids: Seq[Long]) extends WebAPIFree[BranchPayload]
  case class BranchSearch(q: String, city: City) extends WebAPIFree[BranchPayload]
  case class GeoGet(ids: Seq[Long]) extends WebAPIFree[GeoGetPayload]
  case class RegionSearch(q: String) extends WebAPIFree[RegionInfoPayload]
  case class RegionGet(ids: Seq[Int]) extends WebAPIFree[RegionInfoPayload]

  def apply[F[_]](implicit i: Inject[WebAPI.WebAPIFree, F]) = new WebAPI[F]
}

class WebAPI[F[_]](implicit i: Inject[WebAPI.WebAPIFree, F]) {
  import WebAPI._
  type Algebra[T] = Free[F, T]
  def branchGet(ids: Seq[Long]): Algebra[BranchPayload] = Free.inject(BranchGet(ids))
  def branchSearch(q: String, city: City): Algebra[BranchPayload] = Free.inject(BranchSearch(q, city))
  def getGet(ids: Seq[Long]): Algebra[GeoGetPayload] = Free.inject(GeoGet(ids))
  def regionSearch(q: String): Algebra[RegionInfoPayload] = Free.inject(RegionSearch(q))
  def regionGet(ids: Seq[Int]): Algebra[RegionInfoPayload] = Free.inject(RegionGet(ids))
  def regionGetOne(id: Int): Algebra[RegionInfoPayload] = Free.inject(RegionGet(Seq(id)))
}

