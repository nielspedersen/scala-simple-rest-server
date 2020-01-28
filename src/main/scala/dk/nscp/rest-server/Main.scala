package dk.nscp.rest_server

import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.io.StdIn

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val personFormat = jsonFormat3(Person)
  implicit val partialPersonFormat = jsonFormat2(PartialPerson)
  implicit val personLisFormat = jsonFormat1(PersonList)
}

object Main extends App with JsonSupport {

  val host = "localhost"
  val port = 8080

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(20.seconds)
  
  val route: Route = {
    concat(
      get {
        concat (
          path("persons") {
           onSuccess(PersonsDAO.allPersons) {
           case persons: Seq[Person] =>
             complete(PersonList(persons))
           case _ =>
             complete(StatusCodes.InternalServerError)
           }
          },
        path("persons"/IntNumber) { id =>
          onSuccess(PersonsDAO.singlePerson(id)) {
            case Some(person) => complete(person)
            case None => complete("{\"message\": \"No such person!\"}")
          }
        })
      },
      post {
        path("persons") {
          entity(as[PartialPerson]) { person =>
            onSuccess(PersonsDAO.addPerson(person.name, person.age)) {
              case person: Person =>
                complete(person)
              case _ =>
                complete(StatusCodes.InternalServerError)
            }
          }
        }
      },
      delete {
        path("persons"/IntNumber) { id =>
          onSuccess(PersonsDAO.deletePerson(id)) {
            case 0 => complete(s"""{"message": "Deleted person with id $id"}""")
            case 1 => complete(s"""{"message": "No such person with id $id"}""")
          }
        }
      }
    )
  }

  val bindingFuture = Http().bindAndHandle(route, host, port)
  println(s"\nServer running on $host:$port\nhit RETURN to terminate")
  StdIn.readLine()

  bindingFuture.flatMap(_.unbind())
  system.terminate()
}

