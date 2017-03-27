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
import models.{AuthorisationDataModel, SubscriptionReferenceModel, UserFactsModel}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, contentType, _}
import services.{AuthService, RegistrationSubscriptionService}
import traits.ControllerTestSpec
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel

import scala.concurrent.Future

class AgentClientControllerSpec extends ControllerTestSpec {

  private val agent = AuthorisationDataModel(AffinityConstants.agent, ConfidenceLevel.L50, CredentialStrengthConstants.weak)
  private val userFactsModel = Json.toJson(UserFactsModel("John", "Smith", "25 Big House", "Telford", None, None, None, "UK"))

  def setupController(response: String, authority: AuthorisationDataModel, subscriptionSuccess: Boolean): AgentClientController = {

    val mockService = mock[AuthService]
    val mockRegSubService = mock[RegistrationSubscriptionService]
    val subscriptionResponse = if (subscriptionSuccess) Future.successful(response) else Future.failed(new Exception("Error message"))

    when(mockService.getAuthority()(ArgumentMatchers.any()))
      .thenReturn(Future.successful(Some(authority)))

    when(mockRegSubService.subscribeGhostUser(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(subscriptionResponse)

    val actions = new AuthorisedActions(mockService)

    new AgentClientController(actions, mockRegSubService)
  }

  "Calling the .enrolAgent method" when {

    "supplied with a valid model" should {
      val jsonBody = Json.toJson(userFactsModel)
      val fakeRequest = FakeRequest().withJsonBody(jsonBody)

      "if the subscription succeed -> return a success" which {
        lazy val controller = setupController("CGT123456", agent, subscriptionSuccess = true)
        lazy val result = controller.subscribeIndividual()(fakeRequest)

        "should have a status of 200 " in {
          await(result).header.status shouldBe 200
        }

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a string representing the CGT reference" in {
          lazy val data = contentAsString(result)
          lazy val json = Json.parse(data)
          json.as[SubscriptionReferenceModel] shouldBe SubscriptionReferenceModel("CGT123456")
        }
      }

      "return a response that failed" which {
        lazy val controller = setupController("", agent, subscriptionSuccess = false)
        lazy val result = controller.subscribeIndividual()(fakeRequest)

        "has the error code 500" in {
          await(result).header.status shouldBe 500
        }

        "has an error message of 'Enrolment failed'" in {
          lazy val jsonBody = contentAsJson(result)
          (jsonBody \ "message").as[String] shouldBe "Error message"
        }
      }
    }

    "supplied with an invalid model" should {

      val jsonBody = Json.toJson("123456789098765")
      val fakeRequest = FakeRequest().withJsonBody(jsonBody)

      lazy val controller = setupController("", agent, subscriptionSuccess = true)
      lazy val result = controller.subscribeIndividual()(fakeRequest)

      "return a response" which {

        "has the error code 400" in {
          await(result).header.status shouldBe 400
        }

        "has an error message of 'Could not bind Json body'" in {
          lazy val jsonBody = contentAsJson(result)
          (jsonBody \ "message").as[String] shouldBe "Bad Request"
        }
      }
    }
  }
}
