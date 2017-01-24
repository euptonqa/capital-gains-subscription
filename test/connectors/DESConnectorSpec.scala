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
import uk.gov.hmrc.play.http.ws.WSHttp
import scala.concurrent.ExecutionContext.global
import common.Utilities.createRandomNino
import helpers.TestHelper._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.{Upstream4xxResponse, Upstream5xxResponse, _}
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import uk.gov.hmrc.play.test.UnitSpec
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http.logging.SessionId


import scala.concurrent.Future

class DESConnectorSpec extends UnitSpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter{

  class MockHttp extends WSGet with WSPost with WSPut with HttpAuditing {
    override val hooks = Seq(AuditingHook)
    override def appName = "test"
    override def auditConnector: AuditConnector = ???
  }

  val mockWSHttp = mock[MockHttp]

  object TestDESConnector extends DESConnector {
    override val serviceUrl = "test"
    override val environment = "test"
    override val token = "test"
    override val http = mockWSHttp
  }

  before {
    reset(mockWSHttp)
  }

  "httpRds" should {

    "return the http response when a OK status code is read from the http response" in {
      val response = HttpResponse(OK)
      TestDESConnector.httpRds.read("http://", "testUrl", response) shouldBe response
    }

    "return a not found exception when it reads a NOT_FOUND status code from the http response" in {
      intercept[NotFoundException]{
        TestDESConnector.httpRds.read("http://", "testUrl", HttpResponse(NOT_FOUND))
      }
    }
  }

  "Calling .subscribe" should {

    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "return success with a valid safeId and ackRef" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("Submission" -> dummySubscriptionRequestValid)))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestValid))

      result shouldBe SuccessDesResponse(Json.obj("Submission" -> dummySubscriptionRequestValid))
    }

    "return success with an accepted response" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(ACCEPTED, responseJson = Some(Json.obj("Submission" -> dummySubscriptionRequestValid)))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestValid))

      result shouldBe SuccessDesResponse(Json.obj("Submission" -> dummySubscriptionRequestValid))
    }

    "return success with a conflicted submission" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(CONFLICT, responseJson = Some(Json.obj("Submission" -> dummySubscriptionRequestValid)))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestValid))

      result shouldBe SuccessDesResponse(Json.obj("Submission" -> dummySubscriptionRequestValid))
    }

    "return a BAD_REQUEST with an invalid safeId and a valid ackRef" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "error")))))

      val result = await(TestDESConnector.subscribe(dummyInvalidSafeID, dummySubscriptionRequestValid))

      result shouldBe InvalidDesRequest("error")
    }

    "return a BAD_REQUEST with a valid safeID and an invalid ackRef" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "error")))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestBadRequest))

      result shouldBe InvalidDesRequest("error")
    }

    "return a NOT_FOUND error with an ackRef containing 'not found'" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(NOT_FOUND, responseJson = Some(Json.obj("reason" -> "not found")))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestNotFound))

      result shouldBe DesErrorResponse
    }

    "return a SERVICE UNAVAILABLE error with an ackRef containing 'serviceunavailable'" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(Json.obj("reason" -> "serviceunavailable")))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestServiceUnavailable))

      result shouldBe DesErrorResponse
    }

    "return a INTERNAL SERVER ERROR with an ackRef containing 'servererror'" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.obj("reason" -> "servererror")))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestServerError))

      result shouldBe DesErrorResponse
    }

    "return a INTERNAL SERVER ERROR with an ackRef containing 'sapnumbermissing'" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.obj("reason" -> "sapnumbermissing")))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestSapNumberMissing))

      result shouldBe DesErrorResponse
    }

    "return a SERVICE UNAVAILABLE ERROR with an ackRef containing 'notprocessed'" should {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(Json.obj("reason" -> "notprocessed")))))

      val result = await(TestDESConnector.subscribe(dummyValidSafeID, dummySubscriptionRequestNotProcessed))

      result shouldBe DesErrorResponse
    }
  }

  "customDESRead" should {

    "return the HttpResponse on a bad request" in {
      val response = HttpResponse(400)
      await(TestDESConnector.customDESRead("", "", response)) shouldBe response
    }

    "throw a NotFoundException" in {
      val response = HttpResponse(404)
      val ex = intercept[NotFoundException]{
        await(TestDESConnector.customDESRead("", "", response))
      }
      ex.getMessage shouldBe "ETMP returned a Not Found status"
    }

    "return the HttpResponse on a conflict" in {
      val response = HttpResponse(409)
      await(TestDESConnector.customDESRead("", "", response)) shouldBe response
    }

    "throw an InternalServerException" in {
      val response = HttpResponse(500)
      val ex = intercept[InternalServerException]{
        await(TestDESConnector.customDESRead("", "", response))
      }
      ex.getMessage shouldBe "ETMP returned an internal server error"
    }

    "throw an BadGatewayException" in {
      val response = HttpResponse(502)
      val ex = intercept[BadGatewayException]{
        await(TestDESConnector.customDESRead("", "", response))
      }
      ex.getMessage shouldBe "ETMP returned an upstream error"
    }

    "return an Upstream4xxResponse when an uncaught 4xx Http response status is found" in {
      val response = HttpResponse(405)
      val ex = intercept[Upstream4xxResponse]{
        await(TestDESConnector.customDESRead("http://", "testUrl", response))
      }
      ex.getMessage shouldBe "http:// of 'testUrl' returned 405. Response body: 'null'"
    }

    "return an Upstream5xxResponse when an uncaught 5xx Http response status is found" in {
      val response = HttpResponse(505)
      val ex = intercept[Upstream5xxResponse]{
        await(TestDESConnector.customDESRead("http://", "testUrl", response))
      }
      ex.getMessage shouldBe "http:// of 'testUrl' returned 505. Response body: 'null'"
    }
  }

  "Calling .register" should {

  }

  "Calling .obtainBP" when {

    trait Setup extends DESConnector {
      val nino = createRandomNino

      override val serviceUrl = "http://google.com"
      override val environment = "???"
      override val token = "DES"
      override val baseUrl = "/capital-gains-subscription/"
      override val obtainBpUrl = "/obtainBp"

      override val urlHeaderEnvironment = "??? see srcs, found in config"
      override val urlHeaderAuthorization = "??? same as above"
      override val http = mock[WSHttp]
    }

    implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "for an accepted BP request, return success" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(ACCEPTED, responseJson = Some(Json.obj("bp" -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    }

    "for a successful BP request return success" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("bp" -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    }

    "for a conflicted request, return success" in new Setup {


      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(CONFLICT, responseJson = Some(Json.obj("bp" -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    }


    "for a request that triggers a NotFoundException return a NotFoundDesResponse" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(new NotFoundException("")))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe NotFoundDesResponse
    }

    "for a request that triggers an InternalServerException return a DES errorResponse" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(new InternalServerException("")))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe DesErrorResponse
    }

    "return a DesErrorResponse when a BadGatewayException occurs" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(new BadGatewayException("")))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe DesErrorResponse
    }

      "making a call for a bad request, return the reason" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "etmp reason")))))

      val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe InvalidDesRequest("etmp reason")
    }

    "making a call for a request that triggers a NotFoundException return a NotFoundDesResponse" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(new NotFoundException("")))

      val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe NotFoundDesResponse
    }
  }

}