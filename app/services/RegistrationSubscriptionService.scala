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
import models._
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier
import common.Keys.TaxEnrolmentsKeys

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegistrationSubscriptionService @Inject()(dESConnector: DESConnector, taxEnrolmentsConnector: TaxEnrolmentsConnector) {

  def subscribeKnownUser(nino: String)(implicit hc: HeaderCarrier): Future[String] = {
    Logger.warn("Issuing a call to DES (stub) to register and subscribe")
    for {
      sapResponse <- dESConnector.obtainSAP(RegisterIndividualModel(Nino(nino)))
      taxEnrolmentsBody <- taxEnrolmentIssuerKnownUserBody(nino)
      cgtRef <- subscribe(sapResponse, taxEnrolmentsBody)
    } yield cgtRef
  }

  def subscribeGhostUser(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      sapResponse <- dESConnector.obtainSAPGhost(userFactsModel)
      taxEnrolmentsBody <- taxEnrolmentIssuerGhostUserBody(userFactsModel.postCode)
      cgtRef <- subscribe(sapResponse, taxEnrolmentsBody)
    } yield cgtRef
  }

  def subscribeOrganisationUser(companySubmissionModel: CompanySubmissionModel)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      taxEnrolmentsBody <- taxEnrolmentIssuerGhostUserBody(companySubmissionModel.registeredAddress.get.postCode.get)
      cgtRef <- subscribe(companySubmissionModel.sap.get, taxEnrolmentsBody)
    } yield cgtRef
  }

  private[services] def subscribe(sapResponse: DesResponse, taxEnrolmentsBody: EnrolmentIssuerRequestModel)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      sap <- fetchDESResponse(sapResponse)
      subscribeResponse <- dESConnector.subscribe(SubscribeIndividualModel(sap))
      cgtRef <- fetchDESResponse(subscribeResponse)
      enrolmentIssuerRequest <- taxEnrolmentsConnector.getIssuerResponse(cgtRef, Json.toJson(taxEnrolmentsBody))
      issuerResponse <- fetchTaxEnrolmentsResponse(enrolmentIssuerRequest)
      enrolmentSubscriberRequest <- taxEnrolmentsConnector.getSubscriberResponse(cgtRef, Json.toJson(taxEnrolmentSubscriberBody(sap)))
      subscriberResponse <- fetchTaxEnrolmentsResponse(enrolmentSubscriberRequest)
    } yield cgtRef
  }

  private[services] def subscribe(sap: String, taxEnrolmentsBody: EnrolmentIssuerRequestModel)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      subscribeResponse <- dESConnector.subscribe(SubscribeIndividualModel(sap))
      cgtRef <- fetchDESResponse(subscribeResponse)
      enrolmentIssuerRequest <- taxEnrolmentsConnector.getIssuerResponse(cgtRef, Json.toJson(taxEnrolmentsBody))
      issuerResponse <- fetchTaxEnrolmentsResponse(enrolmentIssuerRequest)
      enrolmentSubscriberRequest <- taxEnrolmentsConnector.getSubscriberResponse(cgtRef, Json.toJson(taxEnrolmentSubscriberBody(sap)))
      subscriberResponse <- fetchTaxEnrolmentsResponse(enrolmentSubscriberRequest)
    } yield cgtRef
  }

  //TODO: Refactor into two seperate responses (subscription ref and sap)
  private def fetchDESResponse(response: DesResponse) = {
    response match {
      case SuccessDesResponse(data) => Future.successful(data.as[String])
      case InvalidDesRequest(message) => Future.failed(new Exception(message))
    }
  }

  private def fetchTaxEnrolmentsResponse(response: TaxEnrolmentsResponse) = {
    response match {
      case SuccessTaxEnrolmentsResponse() => Future.successful()
      case InvalidTaxEnrolmentsRequest(message) => Future.failed(new Exception(message))
    }
  }

  def taxEnrolmentIssuerKnownUserBody(nino: String): Future[EnrolmentIssuerRequestModel] = {
    val identifier = Identifier(TaxEnrolmentsKeys.ninoIdentifier, nino)
    Future.successful(EnrolmentIssuerRequestModel(TaxEnrolmentsKeys.serviceName, identifier))
  }

  def taxEnrolmentIssuerGhostUserBody(postcode: String): Future[EnrolmentIssuerRequestModel] = {
    val identifier = Identifier(TaxEnrolmentsKeys.postcodeIdentifier, postcode)
    Future.successful(EnrolmentIssuerRequestModel(TaxEnrolmentsKeys.serviceName, identifier))
  }

  def taxEnrolmentSubscriberBody(sap: String): EnrolmentSubscriberRequestModel =
    EnrolmentSubscriberRequestModel(TaxEnrolmentsKeys.serviceName, TaxEnrolmentsKeys.callbackUrl, sap)
}
