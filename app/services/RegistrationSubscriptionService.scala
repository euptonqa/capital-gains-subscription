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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier
import common.Keys.TaxEnrolmentsKeys

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegistrationSubscriptionService @Inject()(desConnector: DESConnector, taxEnrolmentsConnector: TaxEnrolmentsConnector) {

  //TODO: Tomorrows job is to update this side for the registration component and then finish the subscription.
  def subscribeKnownUser(nino: String)(implicit hc: HeaderCarrier): Future[String] = {
    Logger.info("Issuing a call to DES (stub) to register and subscribe known user")

    val filterDuplicates: DesResponse => Future[DesResponse] = {
      case DuplicateDesResponse =>
        desConnector.getSAPForExistingBP(RegisterIndividualModel(Nino(nino)))
      case x => Future.successful(x)
    }

    for {
      sapResponse <- desConnector.registerIndividualWithNino(RegisterIndividualModel(Nino(nino))).flatMap(filterDuplicates)
      //TODO: this is the wrong place to do this
      taxEnrolmentsBody <- taxEnrolmentIssuerKnownUserBody(nino)
      cgtRef <- subscribe(sapResponse, taxEnrolmentsBody)
    } yield cgtRef
  }

  def subscribeGhostUser(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier): Future[String] = {
    Logger.info("Issuing a call to DES to register and subscribe ghost user")
    for {
      sapResponse <- desConnector.registerIndividualGhost(userFactsModel)
      cgtRef1 <- fetchDESResponse(sapResponse)
      taxEnrolmentsBody <- taxEnrolmentIssuerGhostUserBody(cgtRef1)
      cgtRef <- subscribe(sapResponse, taxEnrolmentsBody)
    } yield cgtRef
  }

  private[services] def subscribe(sapResponse: DesResponse, taxEnrolmentsBody: EnrolmentIssuerRequestModel)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      sap <- fetchDESRegisterResponse(sapResponse)
      subscribeResponse <- desConnector.subscribe(SubscribeIndividualModel(sap))
      cgtRef <- handleSubscriptionResponse(subscribeResponse, taxEnrolmentsBody, sap)
    } yield cgtRef
  }

  def subscribeOrganisationUser(companySubmissionModel: CompanySubmissionModel)(implicit hc: HeaderCarrier): Future[String] = {

    Logger.info("Issuing a call to DES to register and subscribe organisation user")

    for {
      subscribeResponse <- desConnector.subscribe(companySubmissionModel)
      cgtRef1 <- fetchDESResponse(subscribeResponse)
      taxEnrolmentsBody <- taxEnrolmentIssuerGhostUserBody(cgtRef1)
      cgtRef <- handleSubscriptionResponse(subscribeResponse, taxEnrolmentsBody, companySubmissionModel.sap.get)
    } yield cgtRef
  }

  private def handleSubscriptionResponse(desResponse: DesResponse,
                                         taxEnrolmentsBody: EnrolmentIssuerRequestModel,
                                         sap: String)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      cgtRef <- fetchDESResponse(desResponse)
      enrolmentIssuerRequest <- taxEnrolmentsConnector.getIssuerResponse(cgtRef, Json.toJson(taxEnrolmentsBody))
      issuerResponse <- fetchTaxEnrolmentsResponse(enrolmentIssuerRequest)
      enrolmentSubscriberRequest <- taxEnrolmentsConnector.getSubscriberResponse(cgtRef, Json.toJson(taxEnrolmentSubscriberBody(sap)))
      subscriberResponse <- fetchTaxEnrolmentsResponse(enrolmentSubscriberRequest)
    } yield cgtRef
  }

  //TODO: I suggest refactoring this response at the connecctor level to respond with the SuccessDesResponse with a meaningful model in it.
  private def fetchDESRegisterResponse(response: DesResponse) = {
    response match {
      case SuccessfulRegistrationResponse(data) => Future.successful(extractSapFromDesSuccessful(data))
      case InvalidDesRequest(message) => Future.failed(new Exception(message.toString()))
    }
  }

  //This is here to keep the subscribe method as-is for now...
  private def fetchDESResponse(response: DesResponse) = {
    response match {
      case SuccessfulRegistrationResponse(data) => Future.successful(data.as[String])
      case InvalidDesRequest(message) => Future.failed(new Exception(message.toString()))
    }
  }
//
//  //TODO: this should DEFINETLEY be at connector level but to avoid the large re-work that would require I'm putting it here for now
//  private def extractSapFromDesSuccessful(body: JsValue) = {
//    //TODO when this is moved to the connector it should also be replaced by a model, not just a string
//    (body \ "safeId").as[String]
//  }

  private def fetchTaxEnrolmentsResponse(response: TaxEnrolmentsResponse) = {
    response match {
      case SuccessTaxEnrolmentsResponse => Future.successful()
      case InvalidTaxEnrolmentsRequest(message) => Future.failed(new Exception(message.toString()))
    }
  }

  def taxEnrolmentIssuerKnownUserBody(nino: String): Future[EnrolmentIssuerRequestModel] = {
    val identifier = Identifier(TaxEnrolmentsKeys.ninoIdentifier, nino)
    Future.successful(EnrolmentIssuerRequestModel(TaxEnrolmentsKeys.serviceName, identifier))
  }

  def taxEnrolmentIssuerGhostUserBody(cgtRef1: String): Future[EnrolmentIssuerRequestModel] = {
    val identifier = Identifier(TaxEnrolmentsKeys.cgtRefIdentifier, cgtRef1)
    Future.successful(EnrolmentIssuerRequestModel(TaxEnrolmentsKeys.serviceName, identifier))
  }

  def taxEnrolmentSubscriberBody(sap: String): EnrolmentSubscriberRequestModel =
    EnrolmentSubscriberRequestModel(TaxEnrolmentsKeys.serviceName, TaxEnrolmentsKeys.callbackUrl, sap)
}
