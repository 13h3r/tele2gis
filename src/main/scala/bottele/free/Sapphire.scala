package bottele.free

import bottele.City
import bottele.services.SapphireService
import cats.free._
import ru.dgis.sapphire.domain.SearchResult


object Sapphire {
  def apply[F[_]](implicit i: Inject[Sapphire.SapphireFree, F]) = new Sapphire

  trait SapphireFree[T]
  case class Search(text: String, city: City) extends SapphireFree[SearchResult]
}

class Sapphire[F[_]](implicit i: Inject[Sapphire.SapphireFree, F]) {
  import Sapphire._
  def search(text: String, city: City): Free[F, SearchResult] = Free.inject(Search(text, city))
}

object SapphireInterpreter {
}