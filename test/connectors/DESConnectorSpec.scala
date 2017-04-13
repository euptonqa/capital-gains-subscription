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

import audit.Logging
import common.Utilities.createRandomNino
import config.ApplicationConfig
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class DESConnectorSpec extends UnitSpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter {

  val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
  val mockLoggingUtils: Logging = mock[Logging]
  val mockWSHttp: MockHttp = mock[MockHttp]
  implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
  implicit val ec: ExecutionContext = mock[ExecutionContext]

  class MockHttp extends WSGet with WSPost with WSPut with HttpAuditing {
    override val hooks = Seq(AuditingHook)

    override def appName: String = "test"

    override def auditConnector: AuditConnector = mock[AuditConnector]
  }

  object TestDesConnector extends DesConnector(mockAppConfig, mockLoggingUtils) {
    val nino: String = createRandomNino
    override lazy val serviceUrl = "test"
    override lazy val environment = "test"
    override lazy val token = "test"
    override val http: MockHttp = mockWSHttp
  }

  before {
    reset(mockWSHttp)
  }

  val dummySap = "CGTUNIQUEREF"
  val dummyNino = Nino(createRandomNino)
  val errorReason = "Errors"


  "Calling .registerIndividualWithNino" should {

    "return a SuccessfulRegistrationResponse when the connection returns a 200" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("safeId" -> dummySap)))))

      val result = await(TestDesConnector.registerIndividualWithNino(RegisterIndividualModel(dummyNino)))

      result shouldBe SuccessfulRegistrationResponse(RegisteredUserModel(dummySap))
    }

    "return a DuplicateDesResponse when the connection returns a 409" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(CONFLICT)))

      val result = await(TestDesConnector.registerIndividualWithNino(RegisterIndividualModel(dummyNino)))

      result shouldBe DuplicateDesResponse
    }

    "return a DesErrorResponse with a message when any other status is returned" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> errorReason)))))

      val result = await(TestDesConnector.registerIndividualWithNino(RegisterIndividualModel(dummyNino)))

      result shouldBe DesErrorResponse(errorReason)
    }
  }

  "Calling .registerIndividualGhost" should {

    "return a SuccessfulRegistrationResponse when the connection returns a 200" in {

    }

    "return a DesErrorResponse with a message when any other status is returned" in {

    }
  }

  //
  //    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
  //
  //    "return success with an OK" in {
  //      val response = Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("Submission" -> dummySubscriptionRequestValid))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe SuccessDesResponse(Json.obj("Submission" -> dummySubscriptionRequestValid))
  //    }
  //
  //    "return success with an accepted response" in {
  //      val response = Future.successful(HttpResponse(ACCEPTED, responseJson = Some(Json.obj("Submission" -> dummySubscriptionRequestValid))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe SuccessDesResponse(Json.obj("Submission" -> dummySubscriptionRequestValid))
  //    }
  //
  //    "return success with a conflicted submission" should {
  //      val response = Future.successful(HttpResponse(CONFLICT, responseJson = Some(Json.obj("Submission" -> dummySubscriptionRequestValid))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe DuplicateDesResponse
  //    }
  //
  //    "return a BAD_REQUEST with an invalid safeId and a valid reference" should {
  //      val response = Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "error"))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe InvalidDesRequest(Json.obj("reason" -> "error"))
  //    }
  //
  //    "return a BAD_REQUEST with a valid safeID and an invalid reference" should {
  //      val response = Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "error"))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe InvalidDesRequest(Json.obj("reason" -> "error"))
  //    }
  //
  //    "return a NOT_FOUND error with an reference containing 'not found'" should {
  //      val response = Future.successful(HttpResponse(NOT_FOUND, responseJson = Some(Json.obj("reason" -> "not found"))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe DesErrorResponse
  //    }
  //
  //    "return a SERVICE UNAVAILABLE error with an reference containing 'serviceunavailable'" should {
  //      val response = Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(Json.obj("reason" -> "serviceunavailable"))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe DesErrorResponse
  //    }
  //
  //    "return a INTERNAL SERVER ERROR with an reference containing 'servererror'" should {
  //      val response = Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.obj("reason" -> "servererror"))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe DesErrorResponse
  //    }
  //
  //    "return a INTERNAL SERVER ERROR with an reference containing 'sapnumbermissing'" should {
  //      val response = Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.obj("reason" -> "sapnumbermissing"))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe DesErrorResponse
  //    }
  //
  //    "return a SERVICE UNAVAILABLE ERROR with an reference containing 'notprocessed'" should {
  //      val response = Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(Json.obj("reason" -> "notprocessed"))))
  //      val result = await(TestDesConnector.handleSubscriptionForCGTResponse(response, Map("Safe Id" -> "", "Url" -> ""), ""))
  //
  //      result shouldBe DesErrorResponse
  //    }
  //  }

  "Calling .getSAPForExistingBP" should {

    "return a SuccessfulRegistrationResponse when the connection returns a 200" in {

    }

    "return a DesErrorResponse with a message when any other status is returned" in {

    }

    val details = UserFactsModel("joe", "smith", "addressLineOne", "addressLineTwo", Some("city"), Some("county"), Some("postcode"), "country")
    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    //    "for an accepted SAP request" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.successful(HttpResponse(ACCEPTED, responseJson = Some(Json.obj("bp" -> "1234567")))))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return success" in {
    //        await(result) shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    //      }
    //    }
    //
    //    "for a successful request" should {
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("bp" -> "1234567")))))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return success" in {
    //        await(result) shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    //      }
    //    }
    //
    //    "for a conflicted request" should {
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.successful(HttpResponse(CONFLICT)))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return DuplicateDesResponse" in {
    //        await(result) shouldBe DuplicateDesResponse
    //      }
    //    }
    //
    //    "for a request that triggers a NotFoundException" should {
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new NotFoundException("")))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return a NotFoundResponse" in {
    //        await(result) shouldBe NotFoundDesResponse
    //      }
    //    }
    //
    //    "for a request that triggers an InternalServerException" should {
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new InternalServerException("")))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return a DESErrorResponse" in {
    //        await(result) shouldBe DesErrorResponse
    //      }
    //    }
    //
    //    "for a request that triggers a BadGatewayException" should {
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new BadGatewayException("")))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return a DESErrorResponse" in {
    //        await(result) shouldBe DesErrorResponse
    //      }
    //    }
    //
    //    "making a call for a bad request" should {
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "etmp reason")))))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return the json body" in {
    //        await(result) shouldBe InvalidDesRequest(Json.obj("reason" -> "etmp reason"))
    //      }
    //    }
    //
    //    "making a call for a request that triggers a NotFoundException" should {
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new NotFoundException("")))
    //
    //      val result = TestDesConnector.obtainSAPGhost(details)
    //
    //      "return a NotFoundDesResponse" in {
    //        await(result) shouldBe NotFoundDesResponse
    //      }
    //    }
  }

  "Calling .subscribeIndividualForCgt" should {

    "return a SuccessfulSubscriptionResponse when the connection returns a 200" in {

    }

    "return a DesErrorResponse with a message when any other status is returned" in {

    }
    //
    //    val nino = TestDesConnector.nino
    //    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
    //
    //    "for an Accepted request, return success" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.successful(HttpResponse(ACCEPTED, responseJson = Some(Json.obj("bp" -> "1234567")))))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    //    }
    //
    //    "for an OK request return success" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
    //        ArgumentMatchers.any())(ArgumentMatchers.any(),
    //        ArgumentMatchers.any(), ArgumentMatchers.any())).
    //        thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("bp" -> "1234567")))))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    //    }
    //
    //    "for a conflicted request, return success" should {
    //
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
    //        ArgumentMatchers.any())(ArgumentMatchers.any(),
    //        ArgumentMatchers.any(), ArgumentMatchers.any())).
    //        thenReturn(Future.successful(HttpResponse(CONFLICT)))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe DuplicateDesResponse
    //    }
    //
    //
    //    "for a request that triggers a NotFoundException return a NotFoundDesResponse" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
    //        thenReturn(Future.failed(new NotFoundException("")))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe NotFoundDesResponse
    //    }
    //
    //    "for a request that triggers an InternalServerException return a DES errorResponse" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
    //        thenReturn(Future.failed(new InternalServerException("")))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe DesErrorResponse
    //    }
    //
    //    "return a DesErrorResponse when a BadGatewayException occurs" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
    //        thenReturn(Future.failed(new BadGatewayException("")))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe DesErrorResponse
    //    }
    //
    //    "making a call for a bad request, return the json body" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
    //        ArgumentMatchers.any())(ArgumentMatchers.any(),
    //        ArgumentMatchers.any(), ArgumentMatchers.any())).
    //        thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "etmp reason")))))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe InvalidDesRequest(Json.obj("reason" -> "etmp reason"))
    //    }
    //
    //    "making a call for a request that triggers a NotFoundException return a NotFoundDesResponse" should {
    //
    //      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    //        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
    //        thenReturn(Future.failed(new NotFoundException("")))
    //
    //      val result = TestDesConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe NotFoundDesResponse
    //    }
  }

  "Calling .subscribeCompanyForCgt" should {
    val nino = TestDesConnector.nino
    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "return a SuccessfulSubscriptionResponse when the connection returns a 200" in {

    }

    "return a DesErrorResponse with a message when any other status is returned" in {

    }
    //
    //    "return a SuccessDesResponse on an OK response" in {
    //      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("bp" -> "1234567")))))
    //
    //      val result = TestDesConnector.getExistingSap(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe SuccessDesResponse(Json.obj("bp" -> "1234567"))
    //    }
    //
    //    "return an InvalidDesRequest on a BAD_REQUEST response" in {
    //      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(Json.obj("reason" -> "etmp reason")))))
    //
    //      val result = TestDesConnector.getExistingSap(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe InvalidDesRequest(Json.obj("reason" -> "etmp reason"))
    //    }
    //
    //    "return a NotFoundDesResponse with a NOT_FOUND exception" in {
    //      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new NotFoundException("")))
    //
    //      val result = TestDesConnector.getExistingSap(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe NotFoundDesResponse
    //    }
    //
    //    "return a DesErrorResponse with an INTERNAL_SERVER_ERROR exception" in {
    //      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new InternalServerException("")))
    //
    //      val result = TestDesConnector.getExistingSap(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe DesErrorResponse
    //    }
    //
    //    "return a DesErrorResponse with a BAD_GATEWAY exception" in {
    //      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new BadGatewayException("")))
    //
    //      val result = TestDesConnector.getExistingSap(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe DesErrorResponse
    //    }
    //
    //    "return a DesErrorResponse on an exception" in {
    //      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
    //        .thenReturn(Future.failed(new Exception("")))
    //
    //      val result = TestDesConnector.getExistingSap(RegisterIndividualModel(Nino(nino)))
    //
    //      await(result) shouldBe DesErrorResponse
    //    }
    //  }
  }
}
