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
import models._
import org.mockito.ArgumentMatchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AgentService, AuthService, RegistrationSubscriptionService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel

import scala.concurrent.Future

class SubscriptionControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  def setupController(response: String, authority: AuthorisationDataModel, subscriptionSuccess: Boolean): SubscriptionController = {

    val mockService = mock[AuthService]
    val mockRegSubService = mock[RegistrationSubscriptionService]
    val mockAgentService = mock[AgentService]
    val subscriptionResponse = if (subscriptionSuccess) Future.successful(response) else Future.failed(new Exception("Error message"))
    val agentEnrolmentResponse = if (subscriptionSuccess) Future.successful() else Future.failed(new Exception("Enrolment failed"))

    when(mockService.getAuthority()(ArgumentMatchers.any()))
      .thenReturn(Future.successful(Some(authority)))

    when(mockRegSubService.subscribeKnownUser(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(subscriptionResponse)

    when(mockRegSubService.subscribeGhostUser(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(subscriptionResponse)

    when(mockRegSubService.subscribeOrganisationUser(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(subscriptionResponse)

    when(mockAgentService.enrolAgent(ArgumentMatchers.any())(ArgumentMatchers.any()))
      .thenReturn(agentEnrolmentResponse)

    val actions = new AuthorisedActions(mockService)

    new SubscriptionController(actions, mockRegSubService, mockAgentService)
  }

  val individual = AuthorisationDataModel(AffinityConstants.individual, ConfidenceLevel.L200, CredentialStrengthConstants.strong)
  val organisation = AuthorisationDataModel(AffinityConstants.organisation, ConfidenceLevel.L50, CredentialStrengthConstants.strong)
  val agent = AuthorisationDataModel(AffinityConstants.agent, ConfidenceLevel.L50, CredentialStrengthConstants.weak)

  "Calling the subscribeKnownIndividual action" when {

    "the service returns a valid string" should {
      lazy val controller = setupController("CGT123456", individual, subscriptionSuccess = true)
      lazy val result = controller.subscribeKnownIndividual("AA123456A")(FakeRequest("POST", ""))

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
          json.as[SubscriptionReferenceModel] shouldBe SubscriptionReferenceModel("CGT123456")
        }
      }
    }

    "the service returns an error" should {
      lazy val controller = setupController("Error message", individual, subscriptionSuccess = false)
      lazy val result = controller.subscribeKnownIndividual("AA123456A")(FakeRequest("POST", ""))

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
      lazy val controller = setupController("CGT123456", organisation, subscriptionSuccess = false)
      lazy val result = controller.subscribeNonResidentNinoIndividual("AA123456A")(FakeRequest("POST", ""))

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

    "the controller is not passed a valid nino" should {
      lazy val controller = setupController("CGT123456", individual, subscriptionSuccess = false)
      lazy val result = controller.subscribeKnownIndividual("AAAAAAAA")(FakeRequest("POST", ""))

      "return a response" which {
        val data = contentAsString(result)
        val json = Json.parse(data)

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a status code of 400" in {
          (json \ "statusCode").as[Int] shouldBe 400
        }

        "has the message 'Bad Request'" in {
          (json \ "message").as[String] shouldBe "Bad Request"
        }
      }
    }
  }

  "Calling the subscribeNonResidentNinoIndividual action" when {

    "the service returns a valid string" should {
      lazy val controller = setupController("CGT123456", individual, subscriptionSuccess = true)
      lazy val result = controller.subscribeNonResidentNinoIndividual("AA123456A")(FakeRequest("POST", ""))

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
          json.as[SubscriptionReferenceModel] shouldBe SubscriptionReferenceModel("CGT123456")
        }
      }
    }

    "the service returns an error" should {
      lazy val controller = setupController("Error message", individual, subscriptionSuccess = false)
      lazy val result = controller.subscribeNonResidentNinoIndividual("AA123456A")(FakeRequest("POST", ""))

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
      lazy val controller = setupController("Error message", organisation, subscriptionSuccess = false)
      lazy val result = controller.subscribeNonResidentNinoIndividual("AA123456A")(FakeRequest("POST", ""))

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

    "the controller is not passed a valid nino" should {
      lazy val controller = setupController("CGT123456", individual, subscriptionSuccess = false)
      lazy val result = controller.subscribeNonResidentNinoIndividual("AAAAAAAA")(FakeRequest("POST", ""))

      "return a response" which {
        val data = contentAsString(result)
        val json = Json.parse(data)

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a status code of 400" in {
          (json \ "statusCode").as[Int] shouldBe 400
        }

        "has the message 'Bad Request'" in {
          (json \ "message").as[String] shouldBe "Bad Request"
        }
      }
    }
  }

  "Calling the subscribeGhostIndividual action" when {

    "the service returns a valid string" should {
      val userFactsModel = Json.toJson(UserFactsModel("John", "Smith", "25 Big House", "Telford", None, None, None, "UK"))
      val fakeRequest = FakeRequest().withJsonBody(userFactsModel)

      lazy val controller = setupController("CGT123456", individual, subscriptionSuccess = true)
      lazy val result = controller.subscribeGhostIndividual()(fakeRequest)

      "return a status of 200" in {
        status(result) shouldBe 200
      }

      "return a response" which {

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a SubscriptionReferenceModel containing the CGT reference" in {
          val data = contentAsString(result)
          val json = Json.parse(data)
          json.as[SubscriptionReferenceModel] shouldBe SubscriptionReferenceModel("CGT123456")
        }
      }
    }

    "the service returns an error" should {
      val userFactsModel = Json.toJson(UserFactsModel("John", "Smith", "25 Big House", "Telford", None, None, None, "UK"))
      val fakeRequest = FakeRequest().withJsonBody(userFactsModel)

      lazy val controller = setupController("Error message", individual, subscriptionSuccess = false)
      lazy val result = controller.subscribeGhostIndividual()(fakeRequest)

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
      val userFactsModel = Json.toJson(UserFactsModel("John", "Smith", "25 Big House", "Telford", None, None, None, "UK"))
      val fakeRequest = FakeRequest().withJsonBody(userFactsModel)

      lazy val controller = setupController("CGT123456", organisation, subscriptionSuccess = false)
      lazy val result = controller.subscribeGhostIndividual()(fakeRequest)

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

    "the controller is not passed a valid UserFactsModel" should {
      val userFactsModel = Json.obj("x" -> "y")
      val fakeRequest: Request[AnyContent] = FakeRequest().withJsonBody(userFactsModel)

      lazy val controller = setupController("CGT123456", individual, subscriptionSuccess = false)
      lazy val result = controller.subscribeGhostIndividual()(fakeRequest)

      "return a response" which {
        val data = contentAsString(result)
        val json = Json.parse(data)

        "is of the type json" in {
          contentType(result) shouldBe Some("application/json")
        }

        "has a status code of 400" in {
          (json \ "statusCode").as[Int] shouldBe 400
        }

        "has the message 'Bad Request'" in {
          (json \ "message").as[String] shouldBe "Bad Request"
        }
      }
    }
  }

  "Calling the subscribeCompany action" when {

    val addressModel = CompanyAddressModel(Some("Address Line 1"), Some("Address Line 2"), None, None, None, None)

    "passing a valid CompanySubmissionModel in the request" should {

      val submissionModel = Json.toJson(CompanySubmissionModel(Some("123456789098765"), Some(addressModel), Some(addressModel)))
      val fakeRequest = FakeRequest().withJsonBody(submissionModel)

      "when the service returns a valid SubscriptionReferenceModel" should {

        lazy val controller = setupController("CGT123456", organisation, subscriptionSuccess = true)
        lazy val result = controller.subscribeCompany()(fakeRequest)

        "return a status of 200" in {
          status(result) shouldBe 200
        }

        "return a response" which {

          "is of the type json" in {
            contentType(result) shouldBe Some("application/json")
          }

          "has a SubscriptionReferenceModel representing the CGT reference" in {
            val data = contentAsString(result)
            val json = Json.parse(data)
            json.as[SubscriptionReferenceModel] shouldBe SubscriptionReferenceModel("CGT123456")
          }
        }
      }

      "when the service returns an error" should {

        lazy val controller = setupController("Error message", organisation, subscriptionSuccess = false)
        lazy val result = controller.subscribeCompany()(fakeRequest)

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
        lazy val controller = setupController("CGT123456", individual, subscriptionSuccess = false)
        lazy val result = controller.subscribeCompany()(fakeRequest)

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

    "passing an invalid CompanySubmissionModel in the request" when {

      "SAP is empty" should {

        val submissionModel = Json.toJson(CompanySubmissionModel(None, Some(addressModel), Some(addressModel)))
        val fakeRequest = FakeRequest().withJsonBody(submissionModel)

        lazy val controller = setupController("Error message", organisation, subscriptionSuccess = false)
        lazy val result = controller.subscribeCompany()(fakeRequest)

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

      "contactAddress is empty" should {

        val submissionModel = Json.toJson(CompanySubmissionModel(Some("123456789098765"), None, Some(addressModel)))
        val fakeRequest = FakeRequest().withJsonBody(submissionModel)

        lazy val controller = setupController("Error message", organisation, subscriptionSuccess = false)
        lazy val result = controller.subscribeCompany()(fakeRequest)

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

      "registeredAddress is empty" should {

        val submissionModel = Json.toJson(CompanySubmissionModel(Some("123456789098765"), Some(addressModel), None))
        val fakeRequest = FakeRequest().withJsonBody(submissionModel)

        lazy val controller = setupController("Error message", organisation, subscriptionSuccess = false)
        lazy val result = controller.subscribeCompany()(fakeRequest)

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

  "Calling the .enrolAgent method" when {

    "supplied with a valid model" should {
      val jsonBody = Json.toJson(AgentSubmissionModel("123456789098765", "CARN1234567"))
      val fakeRequest = FakeRequest().withJsonBody(jsonBody)

      "return a 204 on a success" in {
        lazy val controller = setupController("", agent, subscriptionSuccess = true)
        lazy val result = controller.enrolAgent()(fakeRequest)

        await(result).header.status shouldBe 204
      }

      "return a response" which {
        lazy val controller = setupController("", agent, subscriptionSuccess = false)
        lazy val result = controller.enrolAgent()(fakeRequest)

        "has the error code 500" in {
          await(result).header.status shouldBe 500
        }

        "has an error message of 'Enrolment failed'" in {
          lazy val jsonBody = contentAsJson(result)
          (jsonBody \ "message").as[String] shouldBe "Enrolment failed"
        }
      }
    }

    "supplied with an invalid model" should {

      val jsonBody = Json.toJson("123456789098765")
      val fakeRequest = FakeRequest().withJsonBody(jsonBody)

      lazy val controller = setupController("", agent, subscriptionSuccess = true)
      lazy val result = controller.enrolAgent()(fakeRequest)

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

    "the request is unauthorised" should {

      val jsonBody = Json.toJson(AgentSubmissionModel("123456789098765", "CARN1234567"))
      val fakeRequest = FakeRequest().withJsonBody(jsonBody)

      lazy val controller = setupController("", organisation, subscriptionSuccess = true)
      lazy val result = controller.enrolAgent()(fakeRequest)

      "return a response" which {

        "has the error code 401" in {
          await(result).header.status shouldBe 401
        }

        "has an error message of 'Unauthorised'" in {
          lazy val jsonBody = contentAsJson(result)
          (jsonBody \ "message").as[String] shouldBe "Unauthorised"
        }
      }
    }
  }
}
