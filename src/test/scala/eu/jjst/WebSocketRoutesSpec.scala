package eu.jjst

//#user-routes-spec
//#test-top
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

//#set-up
class WebSocketRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
    with WebSocketRoutes {

  lazy val routes = websocketRoute

  "WebSocketRoutes" should {
    "return no users if no present (GET /users)" in {
    }

    "be able to add users (POST /users)" in {
    }

    "be able to remove users (DELETE /users)" in {
    }
  }
}
