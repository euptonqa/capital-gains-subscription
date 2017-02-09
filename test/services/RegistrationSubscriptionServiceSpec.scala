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
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class RegistrationSubscriptionServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  implicit val ec = mock[ExecutionContext]
  val mockDESConnector = mock[DESConnector]
  val mockTaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

  def setupMock(cgtRef: DesResponse, issuerResponse: TaxEnrolmentsResponse, subscriberResponse: TaxEnrolmentsResponse,
                sap: Option[DesResponse] = Some(SuccessDesResponse(Json.toJson("sap")))): RegistrationSubscriptionService = {

    when(mockDESConnector.obtainSAP(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(sap.get))

    when(mockDESConnector.obtainSAPGhost(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(sap.get))

    when(mockDESConnector.subscribe(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(cgtRef))

    when(mockTaxEnrolmentsConnector.getIssuerResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(issuerResponse))

    when(mockTaxEnrolmentsConnector.getSubscriberResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(subscriberResponse))

    new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
  }

  val userFactsModel = UserFactsModel("John", "Smith", "25 Big House", None, "Telford", None, "ABC 404", "UK")
  val taxEnrolmentsBody = EnrolmentIssuerRequestModel("", Identifier("", ""))
  val companySubmissionModel = CompanySubmissionModel(Some("123456789"), None, Some(CompanyAddressModel(None, None, None, None, Some(""), None)))

  "Calling RegistrationSubscriptionService .taxEnrolmentIssuerKnownUserBody" should {

    val service = new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
    val result = service.taxEnrolmentIssuerKnownUserBody("AA123456B")

    "return a formatted EnrolmentIssuerRequestModel" in {
      await(result) shouldEqual EnrolmentIssuerRequestModel("HMRC-CGT",
                                  Identifier("NINO", "AA123456B"))
    }
  }

  "Calling RegistrationSubscriptionService .taxEnrolmentIssuerGhostUserBody" should {

    val service = new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
    val result = service.taxEnrolmentIssuerGhostUserBody("ABC 123")

    "return a formatted EnrolmentIssuerRequestModel" in {
      await(result) shouldEqual EnrolmentIssuerRequestModel("HMRC-CGT",
                                  Identifier("POSTCODE", "ABC 123"))
    }
  }

  "Calling RegistrationSubscriptionService .taxEnrolmentSubscriberBody" should {

    val service = new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
    val result = service.taxEnrolmentSubscriberBody("fake sap")

    "return a formatted EnrolmentSubscriberRequestModel" in {
      await(result) shouldEqual EnrolmentSubscriberRequestModel("HMRC-CGT", "", "fake sap")
    }
  }

  "Calling RegistrationSubscriptionService .subscribe" should {

    "with a valid DesResponse for SAP" should {

      val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse(),
        SuccessTaxEnrolmentsResponse()
      )

      val result = await(testService.subscribe(SuccessDesResponse(Json.toJson("fake sap")), taxEnrolmentsBody))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with an sap instead of a DesResponse" should {
      val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse(),
        SuccessTaxEnrolmentsResponse()
      )

      val result = await(testService.subscribe("fake sap", taxEnrolmentsBody))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with a failed DesResponse for registration" should {

      val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse(),
        SuccessTaxEnrolmentsResponse()
      )

      val ex = intercept[Exception] {
        await(testService.subscribe(InvalidDesRequest("error message"), taxEnrolmentsBody))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe "error message"
      }
    }

    "with a failed DesResponse for subscription" should {

      val testService = setupMock(InvalidDesRequest("error message"),
        SuccessTaxEnrolmentsResponse(),
        SuccessTaxEnrolmentsResponse()
      )

      val ex = intercept[Exception] {
        await(testService.subscribe(SuccessDesResponse(Json.toJson("fake sap")), taxEnrolmentsBody))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe "error message"
      }
    }

    "with a failed Tax Enrolments issuer response" should {

      val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        InvalidTaxEnrolmentsRequest("error message"),
        SuccessTaxEnrolmentsResponse()
      )

      val ex = intercept[Exception] {
        await(testService.subscribe(SuccessDesResponse(Json.toJson("fake sap")), taxEnrolmentsBody))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe "error message"
      }
    }

    "with a failed Tax Enrolments subscriber response" should {

      val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse(),
        InvalidTaxEnrolmentsRequest("error message")
      )

      val ex = intercept[Exception] {
        await(testService.subscribe(SuccessDesResponse(Json.toJson("fake sap")), taxEnrolmentsBody))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe "error message"
      }
    }
  }

  "Calling RegistrationSubscriptionService .subscribeKnownUser" should {

    "with a valid request" should {

      val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse(),
        SuccessTaxEnrolmentsResponse(),
        Some(SuccessDesResponse(Json.toJson("sap")))
      )

      val result = await(testService.subscribeKnownUser("AB123456B"))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with an invalid request" should {

      val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
        SuccessTaxEnrolmentsResponse(),
        SuccessTaxEnrolmentsResponse(),
        Some(InvalidDesRequest("error message"))
      )

      val ex = intercept[Exception] {
        await(testService.subscribeKnownUser("AB123456B"))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe "error message"
      }
    }

    "Calling RegistrationSubscriptionService .subscribeGhostUser" should {

      "with a valid request" should {

        val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
          SuccessTaxEnrolmentsResponse(),
          SuccessTaxEnrolmentsResponse(),
          Some(SuccessDesResponse(Json.toJson("sap")))
        )

        val result = await(testService.subscribeGhostUser(userFactsModel))

        "return CGT ref" in {
          result shouldBe "fake cgt ref"
        }
      }

      "with an invalid request" should {

        val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
          SuccessTaxEnrolmentsResponse(),
          SuccessTaxEnrolmentsResponse(),
          Some(InvalidDesRequest("error message"))
        )

        val ex = intercept[Exception] {
          await(testService.subscribeGhostUser(userFactsModel))
        }

        "throw an exception with error message" in {
          ex.getMessage shouldBe "error message"
        }
      }
    }

    "Calling RegistrationSubscriptionService .subscribeOrganisationUser" when {

      "with a valid request" should {

        val testService = setupMock(SuccessDesResponse(Json.toJson("fake cgt ref")),
          SuccessTaxEnrolmentsResponse(),
          SuccessTaxEnrolmentsResponse(),
          Some(SuccessDesResponse(Json.toJson("sap")))
        )

        val result = await(testService.subscribeOrganisationUser(companySubmissionModel))

        "return CGT ref" in {
          result shouldBe "fake cgt ref"
        }
      }

      "with an invalid request" should {

        val testService = setupMock(InvalidDesRequest("error message"),
          SuccessTaxEnrolmentsResponse(),
          SuccessTaxEnrolmentsResponse(),
          Some(InvalidDesRequest("error message"))
        )

        val ex = intercept[Exception] {
          await(testService.subscribeOrganisationUser(companySubmissionModel))
        }

        "throw an exception with error message" in {
          ex.getMessage shouldBe "error message"
        }
      }
    }
  }
}
