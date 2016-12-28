package bottele.free

import bottele.City
import bottele.services.WebAPI.{BranchPayload, GeoGetPayload, RegionInfoPayload}
import cats.free.Free


object WebAPI {
  type Algebra[T] = Free[WebAPIFree, T]

  trait WebAPIFree[T]

  case class BranchGet(ids: Seq[Long]) extends WebAPIFree[BranchPayload]
  case class BranchSearch(q: String, city: City) extends WebAPIFree[BranchPayload]
  case class GeoGet(ids: Seq[Long]) extends WebAPIFree[GeoGetPayload]
  case class RegionSearch(q: String) extends WebAPIFree[RegionInfoPayload]
  case class RegionGet(ids: Seq[Long]) extends WebAPIFree[RegionInfoPayload]

  def branchGet(ids: Seq[Long]): Algebra[BranchPayload] = Free.liftF(BranchGet(ids))
  def branchSearch(q: String, city: City): Algebra[BranchPayload] = Free.liftF(BranchSearch(q, city))
  def getGet(ids: Seq[Long]): Algebra[GeoGetPayload] = Free.liftF(GeoGet(ids))
  def regionSearch(q: String): Algebra[RegionInfoPayload] = Free.liftF(RegionSearch(q))
  def regionGet(ids: Seq[Long]): Algebra[RegionInfoPayload] = Free.liftF(RegionGet(ids))
}