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

import connectors.{SuccessTaxEnrolmentsResponse, TaxEnrolmentsConnector, TaxEnrolmentsErrorResponse, TaxEnrolmentsResponse}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AgentServiceSpec extends UnitSpec with OneAppPerSuite with MockitoSugar {

  def setupService(issuerResponse: TaxEnrolmentsResponse, subscriberResponse: TaxEnrolmentsResponse): AgentService = {

    val mockConnector = mock[TaxEnrolmentsConnector]

    when(mockConnector.getSubscriberAgentResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(subscriberResponse))

    when(mockConnector.getIssuerAgentResponse(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(Future.successful(issuerResponse))

    new AgentService(mockConnector)
  }

  implicit val hc: HeaderCarrier = mock[HeaderCarrier]

  "Calling .enrolAgent" when {

    "subscriber and issuer return successes" should {
      lazy val service = setupService(SuccessTaxEnrolmentsResponse, SuccessTaxEnrolmentsResponse)

      "return a successful enrolment response" in {
        lazy val result = service.enrolAgent("")

        await(result) shouldBe()
      }
    }

    "subscriber returns a success while issuer returns a failure" should {
      lazy val service = setupService(TaxEnrolmentsErrorResponse, SuccessTaxEnrolmentsResponse)

      "throw an exception with Enrolment failed" in {
        lazy val ex = intercept[Exception] {
          await(service.enrolAgent(""))
        }

        ex.getMessage shouldBe "Enrolment failed"
      }
    }

    "issuer returns a success while subscriber returns a failure" should {
      lazy val service = setupService(SuccessTaxEnrolmentsResponse, TaxEnrolmentsErrorResponse)

      "throw an exception with Enrolment failed" in {
        lazy val ex = intercept[Exception] {
          await(service.enrolAgent(""))
        }

        ex.getMessage shouldBe "Enrolment failed"
      }
    }

    "subscriber and issuer return failures" should {
      lazy val service = setupService(TaxEnrolmentsErrorResponse, TaxEnrolmentsErrorResponse)

      "throw an exception with Enrolment failed" in {
        lazy val ex = intercept[Exception] {
          await(service.enrolAgent(""))
        }

        ex.getMessage shouldBe "Enrolment failed"
      }
    }
  }

}
