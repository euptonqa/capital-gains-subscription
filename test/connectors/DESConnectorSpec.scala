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
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse, InternalServerException, NotFoundException}
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.{ArgumentMatchers, Matchers}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Generator

import scala.concurrent.Future
import scala.util.Random
import org.scalatest.BeforeAndAfter

import scala.concurrent.ExecutionContext.global


class DESConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfter with WithFakeApplication {



  def createRandomNino: String = new Generator(new Random()).nextNino.nino.replaceFirst("MA", "AA")

  //val mockWSHttp = mock[WSHttp]

//  before {
//    reset(mockWSHttp)
//  }

  "Calling .subscribe" should {

  }

  "Calling .register" should {

  }

  "Calling .obtainBP" when {

    implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
    "for a successful BP request return success" in new DESConnector {
      val nino = createRandomNino

      override val http = mock[WSHttp]


      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(200, responseJson = Some(Json.obj(nino -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj(nino -> "1234567"))
    }

    "for an accepted BP request, return success" in new DESConnector {
      val nino = createRandomNino

      override val http = mock[WSHttp]

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj(nino -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj(nino -> "1234567"))
    }

    "for a conflicted request, return success" in new DESConnector {
      val nino = createRandomNino
      override val http = mock[WSHttp]

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj(nino -> "1234567")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe SuccessDesResponse(Json.obj(nino -> "1234567"))
    }

    "for an invalid request, return the reason" in new DESConnector {
      val nino = createRandomNino
      override val http = mock[WSHttp]

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(400, responseJson = Some(Json.obj("reason" -> "etmp reason")))))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe InvalidDesRequest("etmp reason")
    }

    "for a request that triggers a NotFoundException return a NotFoundDesResponse" in new DESConnector {
      val nino = createRandomNino
      override val http = mock[WSHttp]

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(new NotFoundException("")))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe NotFoundDesResponse
    }

    "for a request that triggers an InternalServerException return a DES errorResponse" in new DESConnector {
      val nino = createRandomNino
      override val http = mock[WSHttp]

      when(http.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(new InternalServerException("")))

      lazy val result = await(this.obtainBp(nino)(hc, global))

      result shouldBe DesErrorResponse
    }
  }

}
