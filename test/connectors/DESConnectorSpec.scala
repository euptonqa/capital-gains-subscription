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
import helpers.TestHelper._
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

  val dummySap = "123456789098765"
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

    val details = UserFactsModel("joe", "smith", "addressLineOne", "addressLineTwo", Some("city"), Some("county"), Some("postcode"), "country")

    "return a SuccessfulRegistrationResponse when the connection returns a 200" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("safeId" -> dummySap)))))

      val result = await(TestDesConnector.registerIndividualGhost(details))

      result shouldBe SuccessfulRegistrationResponse(RegisteredUserModel(dummySap))
    }

    "return a DesErrorResponse with a message when any other status is returned" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_GATEWAY, responseJson = Some(Json.obj("reason" -> errorReason)))))

      val result = await(TestDesConnector.registerIndividualGhost(details))

      result shouldBe DesErrorResponse(errorReason)
    }
  }

  "Calling .getSAPForExistingBP" should {

    "return a SuccessfulRegistrationResponse when the connection returns a 200" in {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("safeId" -> dummySap)))))

      val result = await(TestDesConnector.getSAPForExistingBP(RegisterIndividualModel(dummyNino)))

      result shouldBe SuccessfulRegistrationResponse(RegisteredUserModel(dummySap))
    }

    "return a DesErrorResponse with a message when any other status is returned" in {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(NOT_FOUND, responseJson = Some(Json.obj("reason" -> errorReason)))))

      val result = await(TestDesConnector.getSAPForExistingBP(RegisterIndividualModel(dummyNino)))

      result shouldBe DesErrorResponse(errorReason)
    }
  }

  "Calling .subscribeIndividualForCgt" should {

    val subscribeIndividualModel = SubscribeIndividualModel("123456789098765")

    "return a SuccessfulSubscriptionResponse when the connection returns a 200" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("subscriptionCGT" -> Json.obj("referenceNumber" -> "DummyCGTRef"))))))

      val result = await(TestDesConnector.subscribeIndividualForCgt(subscribeIndividualModel))

      result shouldBe SuccessfulSubscriptionResponse(SubscriptionReferenceModel("DummyCGTRef"))
    }

    "return a DesErrorResponse with a message when any other status is returned" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.obj("reason" -> errorReason)))))

      val result = await(TestDesConnector.subscribeIndividualForCgt(subscribeIndividualModel))

      result shouldBe DesErrorResponse(errorReason)
    }
  }

  "Calling .subscribeCompanyForCgt" should {

    val companySubmissionModel = CompanySubmissionModel(Some(dummySap), None, Some(CompanyAddressModel(Some("line1"),
      Some("line2"), None, None, Some("XX11 1XX"), Some("DE"))))

    "return a SuccessfulSubscriptionResponse when the connection returns a 200" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.obj("subscriptionCGT" -> Json.obj("referenceNumber" -> "DummyCGTRef"))))))

      val result = await(TestDesConnector.subscribeCompanyForCgt(companySubmissionModel))

      result shouldBe SuccessfulSubscriptionResponse(SubscriptionReferenceModel("DummyCGTRef"))
    }

    "return a DesErrorResponse with a message when any other status is returned" in {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(FORBIDDEN, responseJson = Some(Json.obj("reason" -> errorReason)))))

      val result = await(TestDesConnector.subscribeCompanyForCgt(companySubmissionModel))

      result shouldBe DesErrorResponse(errorReason)
    }
  }
}
