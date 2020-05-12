package eu.jjst

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

//#set-up
class WebSocketRoutesSpec extends WordSpec with Matchers with ScalaFutures {

  "WebSocketRoutes" should {
    "return no users if no present (GET /users)" in {
    }

    "be able to add users (POST /users)" in {
    }

    "be able to remove users (DELETE /users)" in {
    }
  }
}
