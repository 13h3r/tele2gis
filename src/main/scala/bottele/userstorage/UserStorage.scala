package bottele.userstorage

import java.sql.{DriverManager, ResultSet}

import bottele.City
import bottele.TelegramBotAPI.UserId

import scala.concurrent.{ExecutionContext, Future}

trait UserStorage {
  def getCity(user: UserId)(implicit ec: ExecutionContext): Future[Option[City]]
  def setCity(user: UserId, city: City)(implicit ec: ExecutionContext): Future[Unit]
}

class NaiveUserStorage(connString: String) extends UserStorage {
  private def execute[T](q: String)(f: ResultSet => T): T = {
    println(q)
    val conn = DriverManager.getConnection(connString)
    val ps = conn.prepareStatement(q)
    val rs = ps.executeQuery()
    val result = f(rs)
    rs.close()
    ps.close()
    conn.close()
    result
  }
  private def executeUpdate[T](q: String): Int = {
    println(q)
    val conn = DriverManager.getConnection(connString)
    val ps = conn.prepareStatement(q)
    val result = ps.executeUpdate()
    ps.close()
    conn.close()
    result
  }

  implicit class PimpedResultSet(resultSet: ResultSet) {
    def iterator: Iterator[ResultSet] = {
      Iterator
        .continually((resultSet, resultSet.next))
        .takeWhile(_._2)
        .map(x => x._1)
    }

    def map[T](f: ResultSet => T): List[T] = iterator
      .map(f)
      .toList
  }

  override def getCity(user: UserId)(implicit ec: ExecutionContext): Future[Option[City]] = {
    Future {
      execute(s"select city_id from users where user_id = ${user.id}") {
        _.iterator
          .take(1)
          .toList
          .headOption
          .flatMap { rs =>
            Option(rs.getInt(1)).map(City)
          }
      }
    }
  }

  override def setCity(user: UserId, city: City)(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      executeUpdate(s"update users set city_id = ${city.id} where user_id = ${user.id}")
    }.flatMap {
      case 0 =>
        Future(executeUpdate(s"insert into users (city_id, user_id) values (${city.id}, ${user.id})"))
      case _ => Future.successful(())
    }
  }
}

