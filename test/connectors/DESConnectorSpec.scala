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

import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.ArgumentMatchers
import play.api.libs.json.{JsValue, Json}
import scala.concurrent.Future
import org.scalatest.BeforeAndAfter
import scala.concurrent.ExecutionContext.global
import play.api.http.Status._
import common.Utilities.createRandomNino

class DESConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfter with WithFakeApplication {


  "Calling .subscribe" should {

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
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj("bp" -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    }

    "for a successful BP request return success" in new Setup {

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(200, responseJson = Some(Json.obj("bp" -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    }

    "for a conflicted request, return success" in new Setup {


      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj("bp" -> "1234567")))))

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
