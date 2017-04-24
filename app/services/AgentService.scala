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

import javax.inject.Inject

import common.Keys.TaxEnrolmentsKeys
import connectors.{SuccessTaxEnrolmentsResponse, TaxEnrolmentsConnector, TaxEnrolmentsResponse}
import models.{AgentSubmissionModel, EnrolmentIssuerRequestModel, EnrolmentSubscriberRequestModel, Identifier}
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentService @Inject()(taxEnrolmentsConnector: TaxEnrolmentsConnector){

  def enrolAgent(model: AgentSubmissionModel)(implicit hc: HeaderCarrier): Future[Unit] = {

    val identifier = Identifier(TaxEnrolmentsKeys.arnIdentifier, model.arn)
    val issuerModel = EnrolmentIssuerRequestModel(TaxEnrolmentsKeys.cgtAgentEnrolmentKey, identifier)
    val subscriberModel = EnrolmentSubscriberRequestModel(TaxEnrolmentsKeys.cgtAgentEnrolmentKey, TaxEnrolmentsKeys.callbackUrl, model.arn)
    val subscriberRequest = taxEnrolmentsConnector.getSubscriberAgentResponse(model.arn, Json.toJson(subscriberModel))
    val issuerRequest = taxEnrolmentsConnector.getIssuerAgentResponse(model.arn, Json.toJson(issuerModel))

    def compositeResponse(subscriberResponse: TaxEnrolmentsResponse, issuerResponse: TaxEnrolmentsResponse): Future[Unit] =
      (subscriberResponse, issuerResponse) match {
      case (SuccessTaxEnrolmentsResponse, SuccessTaxEnrolmentsResponse) => Future.successful()
      case _ => Future.failed(new Exception("Enrolment failed"))
    }

    for {
      subscriberResponse <- subscriberRequest
      issuerResponse <- issuerRequest
      response <- compositeResponse(subscriberResponse, issuerResponse)
    } yield response
  }
}
