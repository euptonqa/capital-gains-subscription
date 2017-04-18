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
import models.{SubscriptionReferenceModel, _}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class RegistrationSubscriptionServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  implicit val ec: ExecutionContext = mock[ExecutionContext]

  private val defaultRegistrationResponse = SuccessfulRegistrationResponse(RegisteredUserModel("SAP123456789098"))
  private val defaultSubscriptionResponse = SuccessfulSubscriptionResponse(SubscriptionReferenceModel("fake cgt ref"))

  def setupMock(issuerResponse: TaxEnrolmentsResponse = SuccessTaxEnrolmentsResponse,
                subscriberResponse: TaxEnrolmentsResponse = SuccessTaxEnrolmentsResponse,
                //TODO: refactor these to not be options, there is no point.
                registrationResponse: Option[DesResponse] = Some(defaultRegistrationResponse),
                getExistingSapResponse: Option[DesResponse] = Some(defaultRegistrationResponse),
                subscriptionResponse: Option[DesResponse] = Some(defaultSubscriptionResponse)
               ): RegistrationSubscriptionService = {

    val mockDESConnector: DesConnector = mock[DesConnector]
    val mockTaxEnrolmentsConnector: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

    when(mockDESConnector.registerIndividualWithNino(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(registrationResponse.get))

    when(mockDESConnector.registerIndividualGhost(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(registrationResponse.get))

    when(mockDESConnector.getSAPForExistingBP(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(getExistingSapResponse.get))

    when(mockDESConnector.subscribeIndividualForCgt(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(subscriptionResponse.get))

    when(mockDESConnector.subscribeCompanyForCgt(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(subscriptionResponse.get))

    when(mockTaxEnrolmentsConnector.getIssuerResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(issuerResponse))

    when(mockTaxEnrolmentsConnector.getSubscriberResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(subscriberResponse))

    new RegistrationSubscriptionService(mockDESConnector, mockTaxEnrolmentsConnector)
  }

  lazy val userFactsModel = UserFactsModel("John", "Smith", "25 Big House", "Telford", None, None, None, "UK")
  lazy val taxEnrolmentsBody = EnrolmentIssuerRequestModel("", Identifier("", ""))
  lazy val companySubmissionModel = CompanySubmissionModel(Some("123456789098765"), None, Some(CompanyAddressModel(None, None, None, None, Some(""), None)))

  "Calling RegistrationSubscriptionService .createIndividualSubscription" when {

    "subscription succeeds" should {

      lazy val testService = setupMock()

      lazy val result = await(testService.createIndividualSubscription(RegisteredUserModel("SAP123456789098")))

      "return CGT ref" in {
        result shouldBe SubscriptionReferenceModel("fake cgt ref")
      }
    }

    "subscription fails" should {
      lazy val testService = setupMock(subscriptionResponse = Some(DesErrorResponse("Subscription failed")))

      lazy val result = intercept[Exception] {
        await(testService.createIndividualSubscription(RegisteredUserModel("SAP123456789098")))
      }

      "throw an error with the message" in {
        result.getMessage shouldBe "Subscription failed"
      }
    }
  }

  "Calling RegistrationSubscriptionService .subscribeKnownUser" when {

    "a valid request is made with a new user" should {

      lazy val testService = setupMock()

      lazy val result = await(testService.subscribeKnownUser("AB123456B"))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "an registering a new user fails" should {

      lazy val testService = setupMock(
        registrationResponse = Some(DesErrorResponse("reason")))

      lazy val ex = intercept[Exception] {
        await(testService.subscribeKnownUser("AB123456B"))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe "reason"
      }
    }

    "a valid request is made with an existing user" should {

      lazy val testService = setupMock(
        registrationResponse = Some(DuplicateDesResponse),
        getExistingSapResponse = Some(defaultRegistrationResponse)
      )

      lazy val result = await(testService.subscribeKnownUser("AB123456B"))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "an invalid request is made with an existing user" should {

      lazy val testService = setupMock(
        registrationResponse = Some(DuplicateDesResponse),
        getExistingSapResponse = Some(DesErrorResponse("No known SAP"))
      )

      lazy val ex = intercept[Exception] {
        await(testService.subscribeKnownUser("AB123456B"))
      }

      "throw an exception with error message" in {
        ex.getMessage shouldBe "No known SAP"
      }
    }

    "subscription succeeds but tax enrolments issuer fails" should {
      lazy val testService = setupMock(issuerResponse = TaxEnrolmentsErrorResponse)

      lazy val result = intercept[Exception] {
        await(testService.subscribeKnownUser("AB123456B"))
      }

      "throw an error with the message" in {
        result.getMessage shouldBe "Enrolling user for CGT failed"
      }
    }

    "subscription succeeds but tax enrolments subscriber fails" should {
      lazy val testService = setupMock(subscriberResponse = TaxEnrolmentsErrorResponse)

      lazy val result = intercept[Exception] {
        await(testService.subscribeKnownUser("AB123456B"))
      }

      "throw an error with the message" in {
        result.getMessage shouldBe "Enrolling user for CGT failed"
      }
    }
  }

  "Calling RegistrationSubscriptionService .subscribeGhostUser" should {

    "with a valid request" should {

      lazy val testService = setupMock()

      lazy val result = await(testService.subscribeGhostUser(userFactsModel))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with an invalid request" should {

      lazy val testService = setupMock(subscriptionResponse = Some(DesErrorResponse("Subscription failed")))

      lazy val ex = intercept[Exception] {
        await(testService.subscribeGhostUser(userFactsModel))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe "Subscription failed"
      }
    }
  }

  "Calling RegistrationSubscriptionService .subscribeOrganisationUser" when {

    "with a valid request" should {

      lazy val testService = setupMock()

      lazy val result = await(testService.subscribeOrganisationUser(companySubmissionModel))

      "return CGT ref" in {
        result shouldBe "fake cgt ref"
      }
    }

    "with an invalid request" should {

      lazy val testService = setupMock(subscriptionResponse = Some(DesErrorResponse("Subscription failed")))

      lazy val ex = intercept[Exception] {
        await(testService.subscribeOrganisationUser(companySubmissionModel))
      }

      "throw an exception with json body message" in {
        ex.getMessage shouldBe "Subscription failed"
      }
    }
  }
}
