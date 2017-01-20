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

import java.util.UUID

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import uk.gov.hmrc.play.http._
import org.scalatest.BeforeAndAfter
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.http.Status._
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.Future


class TaxEnrolmentsConnectorSpec extends UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfter {

  val mockWSHttp = mock[WSHttp]

  object TestTaxEnrolmentsConnector extends TaxEnrolmentsConnector {
    override val serviceUrl: String = "localhost"
    override val issuerUri: String = "issuer"
    override val subscriberUri: String = "subscriber"
    override val http: HttpPut with HttpGet with HttpPost = mockWSHttp
    override val urlHeaderEnvironment = "test"
    override val urlHeaderAuthorization = "testAuth"
  }

  implicit val hc = mock[HeaderCarrier]
  val jsBody = Json.obj("reason" -> "y")

  def mockResponse(responseStatus: Int, responseJson: JsValue)(implicit headerCarrier: HeaderCarrier) : Unit = {
    when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(HttpResponse(responseStatus = responseStatus, responseJson = Some(responseJson))))
  }

  def mockExceptionResponse(ex: Exception)(implicit headerCarrier: HeaderCarrier) : Unit = {
    when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())
      (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.failed(ex))
  }

  before {
    reset(mockWSHttp)
  }

  "httpRds" should {

    "return the http response when a 200 status code is read from the http response" in {
      val response = HttpResponse(NO_CONTENT)
      TestTaxEnrolmentsConnector.httpRds.read("http://", "testUrl", response) shouldBe response
    }

    "return a bad gateway exception when it reads a 502 status code from the http response" in {
      intercept[BadGatewayException]{
        TestTaxEnrolmentsConnector.httpRds.read("http://", "testUrl", HttpResponse(BAD_GATEWAY))
      }
    }
  }

  "TaxEnrolmentsConnector .getIssuerResponse" should {

    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "with a valid request" should {

      mockResponse(NO_CONTENT, jsBody)

      val result = await(TestTaxEnrolmentsConnector.getIssuerResponse("12345", jsBody))

      "return SuccessTaxEnrolmentsResponse with response body" in {
        result shouldBe SuccessTaxEnrolmentsResponse(jsBody)
      }
    }

    "with an invalid request" should {

      mockResponse(BAD_REQUEST, jsBody)

      val result = await(TestTaxEnrolmentsConnector.getIssuerResponse("12345", jsBody))

      "return InvalidTaxEnrolmentsRequest with reason" in {
        result shouldBe InvalidTaxEnrolmentsRequest("y")
      }
    }

    "when a BadGatewayException occurs" should {

      mockExceptionResponse(new BadGatewayException(""))

      val result = await(TestTaxEnrolmentsConnector.getIssuerResponse("12345", jsBody))

      "return a TaxEnrolmentsErrorResponse" in {
        result shouldBe TaxEnrolmentsErrorResponse
      }
    }

    "when an InternalServerException occurs" should {

      mockExceptionResponse(new InternalServerException(""))

      val result = await(TestTaxEnrolmentsConnector.getIssuerResponse("12345", jsBody))

      "return a TaxEnrolmentsErrorResponse" in {
        result shouldBe TaxEnrolmentsErrorResponse
      }
    }

    "when an uncaught Exception occurs" should {

      mockExceptionResponse(new Exception(""))

      val result = await(TestTaxEnrolmentsConnector.getIssuerResponse("12345", jsBody))

      "return a TaxEnrolmentsErrorResponse" in {
        result shouldBe TaxEnrolmentsErrorResponse
      }
    }
  }

  "TaxEnrolmentsConnector .customTaxEnrolmentsRead" should {

    "with an internal server error" should {

      val response = HttpResponse(INTERNAL_SERVER_ERROR)
      val ex = intercept[InternalServerException] {
        await(TestTaxEnrolmentsConnector.customTaxEnrolmentsRead("", "", response))
      }

      "throw an InternalServerException with custom text" in {
        ex.getMessage shouldBe "Tax Enrolments returned an internal server error"
      }
    }

    "with a bad gateway exception" should {

      val response = HttpResponse(BAD_GATEWAY)

      val ex = intercept[BadGatewayException]{
        await(TestTaxEnrolmentsConnector.customTaxEnrolmentsRead("", "", response))
      }

      "throw a BadGatewayException with custom text" in {
        ex.getMessage shouldBe "Tax Enrolments returned an upstream error"
      }
    }

    "with an uncaught exception" should {

      val response = HttpResponse(UNAUTHORIZED)

      val ex = intercept[Upstream4xxResponse]{
        await(TestTaxEnrolmentsConnector.customTaxEnrolmentsRead("http://", "url", response))
      }

      "throw an Upstream4xxResponse" in {
        ex.getMessage shouldBe "http:// of 'url' returned 401. Response body: 'null'"
      }
    }
  }

  "TaxEnrolmentsConnector .getSubscriberResponse" should {

    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "with a valid request" should {

      mockResponse(NO_CONTENT, jsBody)

      val result = await(TestTaxEnrolmentsConnector.getSubscriberResponse("12345", jsBody))

      "return SuccessTaxEnrolmentsResponse with response body" in {
        result shouldBe SuccessTaxEnrolmentsResponse(jsBody)
      }
    }

    "with a bad request" should {

      mockResponse(BAD_REQUEST, jsBody)

      val result = await(TestTaxEnrolmentsConnector.getSubscriberResponse("12345", jsBody))

      "return InvalidTaxEnrolmentsRequest with reason" in {
        result shouldBe InvalidTaxEnrolmentsRequest("y")
      }
    }

    "with an unauthorised request" should {

      mockResponse(UNAUTHORIZED, jsBody)

      val result = await(TestTaxEnrolmentsConnector.getSubscriberResponse("12345", jsBody))

      "return InvalidTaxEnrolmentsRequest with reason" in {
        result shouldBe InvalidTaxEnrolmentsRequest("y")
      }
    }

    "when a BadGatewayException occurs" should {

      mockExceptionResponse(new BadGatewayException(""))

      val result = await(TestTaxEnrolmentsConnector.getSubscriberResponse("12345", jsBody))

      "return a TaxEnrolmentsErrorResponse" in {
        result shouldBe TaxEnrolmentsErrorResponse
      }
    }

    "when an InternalServerException occurs" should {

      mockExceptionResponse(new InternalServerException(""))

      val result = await(TestTaxEnrolmentsConnector.getSubscriberResponse("12345", jsBody))

      "return a TaxEnrolmentsErrorResponse" in {
        result shouldBe TaxEnrolmentsErrorResponse
      }
    }

    "when an uncaught Exception occurs" should {

      mockExceptionResponse(new Exception(""))

      val result = await(TestTaxEnrolmentsConnector.getSubscriberResponse("12345", jsBody))

      "return a TaxEnrolmentsErrorResponse" in {
        result shouldBe TaxEnrolmentsErrorResponse
      }
    }
  }
}
