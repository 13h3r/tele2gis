package bottele.free

import bottele.City
import cats.free.Free
import ru.dgis.sapphire.domain.SearchResult


object Sapphire {
  type Algebra[T] = Free[SapphireFree, T]

  trait SapphireFree[T]
  case class Search(text: String, city: City) extends SapphireFree[SearchResult]

  def search(text: String, city: City): Algebra[SearchResult] = Free.liftF(Search(text, city))
}
