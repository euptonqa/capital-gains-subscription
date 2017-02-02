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

import javax.inject.{Inject, Singleton}
import connectors._
import models.{FullDetails, RegisterModel, SubscribeModel, SubscriptionRequest}
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class DESService @Inject()(dESConnector: DESConnector, taxEnrolmentsConnector: TaxEnrolmentsConnector) {

  def fetchSap(response: DesResponse) = {
    response match {
      case SuccessDesResponse(data) => {
        Future.successful(data.as[String])}
      case InvalidDesRequest(message) => Future.failed(new Exception(message))
    }
  }

  def fetchCGTReference(response: DesResponse) = {
    response match {
      case SuccessDesResponse(data) => Future.successful(data.as[String])
      case InvalidDesRequest(message) => Future.failed(new Exception(message))
    }
  }

  def subscribeUser(nino: String)(implicit hc: HeaderCarrier): Future[String] = {

    for {
      bpResponse <- dESConnector.obtainBp(RegisterModel(Nino(nino)))
      sap <- fetchSap(bpResponse)
      subscribeResponse <- dESConnector.subscribe(SubscribeModel(sap))
      cgtRef <- fetchCGTReference(subscribeResponse)
      enrolmentIssuerRequest <- taxEnrolmentsConnector.getIssuerResponse(cgtRef, Json.toJson(cgtRef))
      enrolmentSubscriberRequest <- taxEnrolmentsConnector.getSubscriberResponse(cgtRef, Json.toJson(cgtRef))
    } yield cgtRef
  }

  def subscribeGhostUser(fullDetails: FullDetails)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      bpResponse <- dESConnector.obtainBpGhost(fullDetails)
      sap <- fetchSap(bpResponse)
      subscribeResponse <- dESConnector.subscribe(SubscribeModel(sap))
      cgtRef <- fetchCGTReference(subscribeResponse)
      enrolmentIssuerRequest <- taxEnrolmentsConnector.getIssuerResponse(cgtRef, Json.toJson(cgtRef))
      enrolmentsSubscriberRequest <- taxEnrolmentsConnector.getSubscriberResponse(cgtRef, Json.toJson(cgtRef))
    } yield cgtRef
  }
}
