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
import models.{FullDetails, RegisterModel, SubscribeModel}
import org.mockito.ArgumentMatchers
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.global

class DESServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  implicit val hc = mock[HeaderCarrier]

  def mockedService(desResponseBp: DesResponse, sap: SubscribeModel, desResponseSubscribe: DesResponse,
                   taxEnrolmentsResponse: TaxEnrolmentsResponse, nino: Nino): DESService = {

    val dummyFullDetails = FullDetails("john", "smith", "something", None, "something", None, "something", "something")

    val mockDesConnector = mock[DESConnector]

    when(mockDesConnector.obtainBpGhost(ArgumentMatchers.eq(dummyFullDetails))(ArgumentMatchers.any(), ArgumentMatchers.any())).
      thenReturn(Future.successful(desResponseBp))

    when(mockDesConnector.obtainBp(ArgumentMatchers.eq(RegisterModel(nino)))(ArgumentMatchers.any(), ArgumentMatchers.any())).
      thenReturn(Future.successful(desResponseBp))

    when(mockDesConnector.subscribe(ArgumentMatchers.eq(sap))(ArgumentMatchers.any())).thenReturn(Future.successful(desResponseSubscribe))


    val mockTaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

    val jsBody = Json.obj("reason" -> "y")

    when(mockTaxEnrolmentsConnector.getSubscriberResponse(ArgumentMatchers.any(), ArgumentMatchers.eq(jsBody))(ArgumentMatchers.any())).
      thenReturn(Future.successful(taxEnrolmentsResponse))

    when(mockTaxEnrolmentsConnector.getIssuerResponse(ArgumentMatchers.any(), ArgumentMatchers.eq(jsBody))(ArgumentMatchers.any())).
      thenReturn(Future.successful(taxEnrolmentsResponse))

    new DESService(mockDesConnector, mockTaxEnrolmentsConnector)
  }

  "Calling DESService .subscribeUser" should {

    "return a CGT ref with a valid request" in {
      def nino = common.Utilities.createRandomNino

      val jsBody = Json.obj("reason" -> "y")
      val service = mockedService(SuccessDesResponse(jsBody), SubscribeModel("CGT-REF"), SuccessDesResponse(jsBody),
        SuccessTaxEnrolmentsResponse(jsBody), Nino(nino))
      val result = service.subscribeUser(nino)

      await(result) shouldBe SubscribeModel("CGT-REF")
    }

  }
}
