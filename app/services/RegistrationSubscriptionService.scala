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

import common.Keys.TaxEnrolmentsKeys
import connectors._
import models._
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegistrationSubscriptionService @Inject()(desConnector: DesConnector, taxEnrolmentsConnector: TaxEnrolmentsConnector) {

  //TODO: Tomorrows job is to update this side for the registration component and then finish the subscription.
  def subscribeKnownUser(nino: String)(implicit hc: HeaderCarrier): Future[String] = {

    val registerModel = RegisterIndividualModel(Nino(nino))

    Logger.info("Issuing a call to DES (stub) to register and subscribe known user")

    def retrieveExistingUsersSap(): Future[RegisteredUserModel] = {
      desConnector.getSAPForExistingBP(registerModel).map {
        case SuccessfulRegistrationResponse(data) => data
        case DesErrorResponse(message) => throw new Exception(message)
      }
    }

    def registerKnownIndividual(): Future[RegisteredUserModel] = {
      desConnector.registerIndividualWithNino(registerModel).flatMap {
        case SuccessfulRegistrationResponse(data) => Future.successful(data)
        case DuplicateDesResponse => retrieveExistingUsersSap()
        case DesErrorResponse(message) => throw new Exception(message)
      }
    }

    for {
      registeredIndividualModel <- registerKnownIndividual()
      subscribedUserModel <- createSubscription(registeredIndividualModel)
      //Enrolments goes here
    } yield cgtRef
  }

//  def subscribeGhostUser(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier): Future[String] = {
//    Logger.info("Issuing a call to DES to register and subscribe ghost user")
//    for {
//      sapResponse <- desConnector.registerIndividualGhost(userFactsModel)
//      cgtRef1 <- fetchDESResponse(sapResponse)
//      taxEnrolmentsBody <- taxEnrolmentIssuerGhostUserBody(cgtRef1)
//      cgtRef <- createSubscription(sapResponse, taxEnrolmentsBody)
//    } yield cgtRef
//  }

  def createSubscription(registeredUser: RegisteredUserModel)(implicit hc: HeaderCarrier): Future[SubscriptionReferenceModel] = {
    desConnector.subscribeIndividualForCgt(SubscribeIndividualModel(registeredUser.safeId)).map {
      case SuccessfulSubscriptionResponse(data) => data
      case DesErrorResponse(message) => throw new Exception(message)
    }
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

  private def enrolUserToGG(subscribedIndividual: String)(implicit hc: HeaderCarrier): Future[String] = {
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
