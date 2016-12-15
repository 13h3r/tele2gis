package bottele.services

import bottele.City
import cats.data.NonEmptyList
import ru.dgis.sapphire.SapphireClient
import ru.dgis.sapphire.domain.{Match, Searchers, Tables}

import scala.concurrent.{ExecutionContext, Future}

object SapphireService {
  sealed trait Item
  final case class Geo(id: Long) extends Item
  final case class Branch(id: Long) extends Item

  sealed trait SearchResult
  final case class Serp(items: NonEmptyList[Item]) extends SearchResult
  final case class Vital(item: Item) extends SearchResult
  final case object Empty extends SearchResult

}
case class SapphireService(client: SapphireClient) {
  import SapphireService._

  private def toItem(m: Match): Item = {
    if (m.table == Tables.branch)
      Branch(m.id.toLong)
    else
      Geo(m.id.toLong)
  }

  def search(text: String, city: City)(
    implicit ec: ExecutionContext
  ): Future[SearchResult] = {
    client
      .search(text, city.id, "ru", Searchers.ExpandingSearcher)
      .map { result =>
        result.matches.toList match {
          case Nil => Empty
          case vital :: Nil => Vital(toItem(vital))
          case m@(first :: tail) =>
            if (first.vital.getOrElse(false) && tail.take(5).forall(_.vital == false)) {
              Vital(toItem(first))
            } else {
              Serp(NonEmptyList.fromListUnsafe(m.map(toItem).take(5)))
            }
        }
      }
  }
}
