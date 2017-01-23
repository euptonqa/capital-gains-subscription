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

import connectors.AuthConnector
import models.AuthorisationDataModel
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class AuthServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  implicit val hc = mock[HeaderCarrier]

  def mockedService(response: Option[AuthorisationDataModel]): AuthService = {

    val mockConnector = mock[AuthConnector]

    when(mockConnector.getAuthResponse()(ArgumentMatchers.any()))
      .thenReturn(Future.successful(response))

    new AuthService(mockConnector)
  }

  "Calling AuthService .getAuthData" should {

    "return an AuthDataModel with a valid request" in {
      val service = mockedService(Some(AuthorisationDataModel("Individual", ConfidenceLevel.L200, "strong")))
      val result = service.getAuthority()

      await(result) shouldBe Some(AuthorisationDataModel("Individual", ConfidenceLevel.L200, "strong"))
    }

    "return a None with an invalid request" in {
      val service = mockedService(None)
      val result = service.getAuthority()

      await(result) shouldBe None
    }
  }
}
