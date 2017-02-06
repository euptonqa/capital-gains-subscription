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

package controllers

import auth.AuthorisedActions
import common.{AffinityConstants, CredentialStrengthConstants}
import models.AuthorisationDataModel
import org.mockito.ArgumentMatchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AuthService, RegistrationSubscriptionService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class SubscriptionControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  def setupController(response: String, errors: Boolean, authorised: Boolean): SubscriptionController = {

    val mockService = mock[AuthService]
    val mockRegSubService = mock[RegistrationSubscriptionService]
    val authority = if (authorised) AuthorisationDataModel(AffinityConstants.individual, ConfidenceLevel.L200, CredentialStrengthConstants.strong)
    else AuthorisationDataModel(AffinityConstants.organisation, ConfidenceLevel.L50, CredentialStrengthConstants.weak)

    when(mockService.getAuthority()(ArgumentMatchers.any()))
      .thenReturn(Future.successful(Some(authority)))

    val actions = new AuthorisedActions(mockService)

    new SubscriptionController(actions, mockRegSubService) {
      override def subscribeUser(nino: Nino)(implicit hc: HeaderCarrier): Future[String] = if (errors) {
        Future.failed(new Exception(response))
      } else {
        Future.successful(response)
      }
    }
  }

  "Calling the subscribeResidentIndividual action" when {

    "the service returns a valid string" should {
      lazy val controller = setupController("CGT123456", errors = false, authorised = true)
      lazy val result = controller.subscribeResidentIndividual("AA123456A")(FakeRequest("GET", ""))

      "return a status of 200" in {
        status(result) shouldBe 200
      }

      "return a response" which {

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a string representing the CGT reference" in {
          val data = contentAsString(result)
          val json = Json.parse(data)
          json.as[String] shouldBe "CGT123456"
        }
      }
    }

    "the service returns an error" should {
      lazy val controller = setupController("Error message", errors = true, authorised = true)
      lazy val result = controller.subscribeResidentIndividual("AA123456A")(FakeRequest("GET", ""))

      "return a status of 500" in {
        status(result) shouldBe 500
      }

      "return a response" which {
        val data = contentAsString(result)
        val json = Json.parse(data)

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a status code of 500" in {
          (json \ "statusCode").as[Int] shouldBe 500
        }

        "has the message from the exception" in {
          (json \ "message").as[String] shouldBe "Error message"
        }
      }
    }

    "the user is unauthorised" should {
      lazy val controller = setupController("CGT123456", errors = false, authorised = false)
      lazy val result = controller.subscribeResidentIndividual("AA123456A")(FakeRequest("GET", ""))

      "return a response" which {
        val data = contentAsString(result)
        val json = Json.parse(data)

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a status code of 401" in {
          (json \ "statusCode").as[Int] shouldBe 401
        }

        "has the message 'Unauthorised'" in {
          (json \ "message").as[String] shouldBe "Unauthorised"
        }
      }
    }

    "the controller was not passed a valid nino" should {
      lazy val controller = setupController("CGT123456", errors = false, authorised = true)
      lazy val result = controller.subscribeResidentIndividual("AA123456")(FakeRequest("GET", ""))

      "return a response" which {
        val data = contentAsString(result)
        val json = Json.parse(data)

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a status code of 401" in {
          (json \ "statusCode").as[Int] shouldBe 401
        }

        "has the message 'Unauthorised'" in {
          (json \ "message").as[String] shouldBe "Unauthorised"
        }
      }
    }
  }
}
