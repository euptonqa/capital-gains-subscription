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

package services

import connectors._
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class RegistrationSubscriptionServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  implicit val ec: ExecutionContext = mock[ExecutionContext]
  lazy val mockDESConnector: DesConnector = mock[DesConnector]
  lazy val mockTaxEnrolmentsConnector: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

  def setupMock(cgtRef: DesResponse, issuerResponse: TaxEnrolmentsResponse, subscriberResponse: TaxEnrolmentsResponse,
                sap: Option[DesResponse] = Some(SuccessDesResponse(Json.obj("safeId" -> "123456789098765"))),
                getExistingSapResponse: Option[DesResponse] = Some(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")))):
  RegistrationSubscriptionService = {

    when(mockDESConnector.obtainSAP(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(sap.get))

    when(mockDESConnector.obtainSAPGhost(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(sap.get))

    when(mockDESConnector.subscribe(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(cgtRef))

    when(mockDESConnector.getExistingSap(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(getExistingSapResponse.get))

    when(mockTaxEnrolmentsConnector.getIssuerResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(issuerResponse))

    when(mockTaxEnrolmentsConnector.getSubscriberResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(subscriberResponse))

    new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
  }

  lazy val userFactsModel = UserFactsModel("John", "Smith", "25 Big House", "Telford", None, None, None, "UK")
  lazy val taxEnrolmentsBody = EnrolmentIssuerRequestModel("", Identifier("", ""))
  lazy val companySubmissionModel = CompanySubmissionModel(Some("123456789098765"), None, Some(CompanyAddressModel(None, None, None, None, Some(""), None)))

  "Calling RegistrationSubscriptionService .taxEnrolmentIssuerKnownUserBody" should {

    lazy val service = new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
    lazy val result = service.taxEnrolmentIssuerKnownUserBody("AA123456B")

    "return a formatted EnrolmentIssuerRequestModel" in {
      await(result) shouldEqual EnrolmentIssuerRequestModel("HMRC-CGT",
        Identifier("NINO", "AA123456B"))
    }
  }

  "Calling RegistrationSubscriptionService .taxEnrolmentIssuerGhostUserBody" should {

    lazy val service = new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
    lazy val result = service.taxEnrolmentIssuerGhostUserBody("CGTREF")

    "return a formatted EnrolmentIssuerRequestModel" in {
      await(result) shouldEqual EnrolmentIssuerRequestModel("HMRC-CGT",
        Identifier("CGTREF1", "CGTREF"))
    }
  }

  "Calling RegistrationSubscriptionService .taxEnrolmentSubscriberBody" should {

    lazy val service = new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
    lazy val result = service.taxEnrolmentSubscriberBody("123456789098765")

    "return a formatted EnrolmentSubscriberRequestModel" in {
      await(result) shouldEqual EnrolmentSubscriberRequestModel("HMRC-CGT", "", "123456789098765")
    }
  }

  "Calling RegistrationSubscriptionService .subscribe" should {

    "with a valid DesResponse for SAP" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse
      )

      lazy val result = await(testService.subscribe(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")), taxEnrolmentsBody))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with an sap instead of a DesResponse" should {
      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse
      )

      lazy val result = await(testService.subscribeOrganisationUser(companySubmissionModel))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with a failed DesResponse for registration" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribe(InvalidDesRequest(Json.obj("reason" -> "y")), taxEnrolmentsBody))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }

    "with a failed DesResponse for subscription" should {

      lazy val testService = setupMock(InvalidDesRequest(Json.obj("reason" -> "y")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribe(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")), taxEnrolmentsBody))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }

    "with a failed Tax Enrolments issuer response" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        InvalidTaxEnrolmentsRequest(Json.obj("reason" -> "y")),
        SuccessTaxEnrolmentsResponse
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribe(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")), taxEnrolmentsBody))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }

    "with a failed Tax Enrolments subscriber response" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        InvalidTaxEnrolmentsRequest(Json.obj("reason" -> "y"))
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribe(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")), taxEnrolmentsBody))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }
  }

  "Calling RegistrationSubscriptionService .subscribeKnownUser" when {

    "a valid request is made with a new user" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")))
      )

      lazy val result = await(testService.subscribeKnownUser("AB123456B"))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "an invalid request is made with a new user" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(InvalidDesRequest(Json.obj("reason" -> "y")))
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribeKnownUser("AB123456B"))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }

    "a valid request is made with an existing user" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(DuplicateDesResponse)
      )

      lazy val result = await(testService.subscribeKnownUser("AB123456B"))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "an invalid request is made with an existing user" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(DuplicateDesResponse),
        Some(InvalidDesRequest(Json.obj("reason" -> "y")))
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribeKnownUser("AB123456B"))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }

  }

  "Calling RegistrationSubscriptionService .subscribeGhostUser" should {

    "with a valid request" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")))
      )

      lazy val result = await(testService.subscribeGhostUser(userFactsModel))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with an invalid request" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(InvalidDesRequest(Json.obj("reason" -> "y")))
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribeGhostUser(userFactsModel))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }
  }

  "Calling RegistrationSubscriptionService .subscribeOrganisationUser" when {

    "with a valid request" should {

      lazy val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(SuccessDesResponse(Json.obj("safeId" -> "123456789098765")))
      )

      lazy val result = await(testService.subscribeOrganisationUser(companySubmissionModel))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with an invalid request" should {

      lazy val testService = setupMock(InvalidDesRequest(Json.obj("reason" -> "y")),
        SuccessTaxEnrolmentsResponse,
        SuccessTaxEnrolmentsResponse,
        Some(InvalidDesRequest(Json.obj("reason" -> "y")))
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribeOrganisationUser(companySubmissionModel))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe Json.obj("reason" -> "y").toString()
      }
    }
  }
}
