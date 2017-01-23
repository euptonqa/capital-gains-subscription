/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import org.mockito.ArgumentMatchers
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse}
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.http.Status._
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel

import scala.concurrent.Future

class AuthConnectorSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  implicit val hc = mock[HeaderCarrier]

  def setupConnector(status: Int, strength: String, affinity: String, confidenceLevel: String): AuthConnector = {

    def authorityResponse(affinity: String, strength: String, confidenceLevel: String): JsValue = Json.parse(
      s"""{"uri":"/auth/oid/57e915480f00000f006d915b","confidenceLevel":$confidenceLevel,"credentialStrength":"$strength",
          |"userDetailsLink":"http://localhost:9978/user-details/id/000000000000000000000000","legacyOid":"00000000000000000000000",
          |"new-session":"/auth/oid/57e915480f00000f006d915b/session","ids":"/auth/oid/57e915480f00000f006d915b/ids",
          |"credentials":{"gatewayId":"000000000000000"},"accounts":{"paye":{"link":"test","nino":"AA123456A"}},"lastUpdated":"2016-09-26T12:32:08.734Z",
          |"loggedInAt":"2016-09-26T12:32:08.734Z","levelOfAssurance":"1","enrolments":"/auth/oid/00000000000000000000000/enrolments",
          |"affinityGroup":"$affinity","correlationId":"0000000000000000000000000000000000000000000000000000000000000000","credId":"000000000000000"}""".stripMargin
    )

    val mockHttp = mock[HttpGet]

    when(mockHttp.GET[HttpResponse](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(HttpResponse(status, Some(authorityResponse(affinity, strength, confidenceLevel)))))

    new AuthConnector {
      override val http = mockHttp
    }
  }

  "Calling the getAuthResponse method" when {

    "the response returned is not a 200" should {
      val connector = setupConnector(BAD_REQUEST, "strong", "Individual", "200")
      lazy val result = connector.getAuthResponse()

      "return a None" in {
        await(result) shouldBe None
      }
    }

    "the response returned is a 200 with valid authority" should {
      val connector = setupConnector(OK, "strong", "Individual", "200")
      lazy val result = connector.getAuthResponse()

      "return a credential strength of 'strong'" in {
        await(result).get.credentialStrength shouldBe "strong"
      }

      "return an affinity group of 'Individual'" in {
        await(result).get.affinityGroup shouldBe "Individual"
      }

      "return a confidence level of 200" in {
        await(result).get.confidenceLevel shouldBe ConfidenceLevel.L200
      }
    }

    "the response returned is a 200 with invalid authority" should {
      val connector = setupConnector(OK, "weak", "Organisation", "50")
      lazy val result = connector.getAuthResponse()

      "return a credential strength of 'weak'" in {
        await(result).get.credentialStrength shouldBe "weak"
      }

      "return an affinity group of 'Organisation'" in {
        await(result).get.affinityGroup shouldBe "Organisation"
      }

      "return a confidence level of 50" in {
        await(result).get.confidenceLevel shouldBe ConfidenceLevel.L50
      }
    }
  }
}
