import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.Database
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.StdIn;

object Server {

  // Case classes to hold the data types in the API calls
  final case class PlanetNoId(name: String, climate: String, terrain: String, numFilms: Int)

  final case class Planet(id: Long, name: String, climate: String,
                          terrain: String, numFilms: Int)

  final case class PlanetList(planets: List[Planet])

  // JSON formats for marshalling requests and responses
  implicit val planetFormat = jsonFormat5(Planet)
  implicit val planetNoIdFormat = jsonFormat4(PlanetNoId)
  implicit val planetListFormat = jsonFormat1(PlanetList)

  // Connect to the database, specified in application.conf
  val db = Database.forConfig("mydb")

  // Simple functions to transform the API calls into SQL queries
  def fetchPlanetById(planetId: Long): Option[Planet] =  {
    val action = sql"select p.id,p.planetname,p.climate,p.terrain,p.numfilms from planets p where p.id = ${planetId};"
      .as[(Long,String,String,String,Int)]
    val list = Await.result(db.run(action),Duration.Inf).map(r => Planet(r._1,r._2,r._3,r._4,r._5)).toList
    list match {
      case Nil => None
      case p :: _ => Some(p)
    }
  }

  def fetchPlanetByName(planetName: String): Option[Planet] =  {
    val action = sql"""select p.id,p.planetname,p.climate,p.terrain,p.numfilms from planets p
      where p.planetname = ${planetName};""".as[(Long,String,String,String,Int)]
    val list = Await.result(db.run(action),Duration.Inf).map(r => Planet(r._1,r._2,r._3,r._4,r._5)).toList
    list match {
      case Nil => None
      case p :: _ => Some(p)
    }
  }

  def fetchPlanetList(): List[Planet] = {
    val action = sql"select id,planetname,climate,terrain,numfilms from planets;"
      .as[(Long,String,String,String,Int)]
    Await.result(db.run(action),Duration.Inf).map(r => Planet(r._1,r._2,r._3,r._4,r._5)).toList
  }

  def addPlanet(p: PlanetNoId) = {
    val action = sql"""insert into planets (planetname,climate,terrain,numfilms) values
      (${p.name},${p.climate},${p.terrain},${p.numFilms});""".as[Long]
    Await.result(db.run(action),Duration.Inf)
  }

  def deletePlanetById(id: Long)  = {
    val action = sql"delete from planets where id = ${id}"
      .as[Long]
    Await.result(db.run(action),Duration.Inf)
  }

  def main(args: Array[String]): Unit = {

    // Initialize Akka actors, necessary for the Http server to work.
    implicit val actorSystem = ActorSystem("starwars-api")
    implicit val actorMaterializer = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher

    // Wire all the routes with ScalaDSL
    val route: Route = concat(
      get {
        pathPrefix("planet-id" / LongNumber) { id =>
          val maybePlanet: Option[Planet] = fetchPlanetById(id)
          maybePlanet match {
            case Some(planet) => complete(planet)
            case None => complete(StatusCodes.NotFound)
          }
        }
      },
      get {
        pathPrefix("planet-name" / Segment) { name =>
          val maybePlanet: Option[Planet] = fetchPlanetByName(name)
          maybePlanet match {
            case Some(planet) => complete(planet)
            case None => complete(StatusCodes.NotFound)
          }
        }
      },
      get {
        path("list-planets") {
          complete(fetchPlanetList())
        }
      },
      post {
        path("add-planet") {
          entity(as[PlanetNoId]) { planet =>
            addPlanet(planet)
            complete("planet added")
          }
        }
      },
      delete {
        path("delete-planet-id" / LongNumber) { id =>
          deletePlanetById(id)
          complete("planet deleted")
        }
      }
    )

    val bindingFuture = Http().bindAndHandle(route,"localhost",8080)

    println(s"Server listening on http://localhost:8080/\nPress ENTER to stop...")

    // Wait for the user to press ENTER to exit
    StdIn.readLine()

    // Clean up everything and exit graciously
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }
}
