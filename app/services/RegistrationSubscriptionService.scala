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

    def retrieveExistingUsersSap(registerModel: RegisterIndividualModel): Future[RegisteredUserModel] = {
      desConnector.getSAPForExistingBP(registerModel).map {
        case SuccessfulRegistrationResponse(data) => data
        case DesErrorResponse(message) => throw new Exception(message)
      }
    }

    def registerKnownIndividual(): Future[RegisteredUserModel] = {
      desConnector.registerIndividualWithNino(registerModel).flatMap {
        case SuccessfulRegistrationResponse(data) => Future.successful(data)
        case DuplicateDesResponse => retrieveExistingUsersSap(registerModel)
        case DesErrorResponse(message) => throw new Exception(message)
      }
    }

    for {
      registeredIndividualModel <- registerKnownIndividual()
      subscribedUserModel <- createIndividualSubscription(registeredIndividualModel)
      //This is not an ideal refactor but it still reads OK
      _ <- enrolUserForCGT(registeredIndividualModel.safeId, subscribedUserModel.cgtRef)
    } yield subscribedUserModel.cgtRef
  }

  def subscribeGhostUser(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier): Future[String] = {

    Logger.info("Issuing a call to DES to register and subscribe ghost user")

    def registerGhostIndividual(): Future[RegisteredUserModel] = {
      desConnector.registerIndividualGhost(userFactsModel).flatMap {
        case SuccessfulRegistrationResponse(data) => Future.successful(data)
        case DesErrorResponse(message) => throw new Exception(message)
      }
    }

    for {
      registeredIndividualModel <- registerGhostIndividual()
      subscribedUserModel <- createIndividualSubscription(registeredIndividualModel)
      //This is not an ideal refactor but it still reads OK
      _ <- enrolUserForCGT(registeredIndividualModel.safeId, subscribedUserModel.cgtRef)
    } yield subscribedUserModel.cgtRef
  }

  def createIndividualSubscription(registeredUser: RegisteredUserModel)(implicit hc: HeaderCarrier): Future[SubscriptionReferenceModel] = {
    desConnector.subscribeIndividualForCgt(SubscribeIndividualModel(registeredUser.safeId)).map {
      case SuccessfulSubscriptionResponse(data) => data
      case DesErrorResponse(message) => throw new Exception(message)
    }
  }

  def subscribeOrganisationUser(companySubmissionModel: CompanySubmissionModel)(implicit hc: HeaderCarrier): Future[String] = {

    Logger.info("Issuing a call to DES to register and subscribe organisation user")

    def createCompanySubscription()(implicit hc: HeaderCarrier): Future[SubscriptionReferenceModel] = {
      desConnector.subscribeCompanyForCgt(companySubmissionModel).map {
        case SuccessfulSubscriptionResponse(data) => data
        case DesErrorResponse(message) => throw new Exception(message)
      }
    }

    for {
      subscribedUserModel <- createCompanySubscription()
      _ <- enrolUserForCGT(companySubmissionModel.sap, subscribedUserModel.cgtRef)
    } yield subscribedUserModel.cgtRef
  }

  //Calling a method purely for side effects ;____;
  private def enrolUserForCGT(sap: String, cgtRef: String)(implicit hc: HeaderCarrier): Future[Unit] = {

    //Avoided refactoring TaxEnrollments connector as I'm told we may not be using it for much longer.
    val taxEnrolmentsIssuerRequestBody = Json.obj(
      "serviceName" -> "CGT",
      "identifiers" -> Seq(Json.obj(
        "name" -> "cgtRef",
        "value" -> cgtRef
      ),
        Json.obj(
          "name" -> "cgtRef1",
          "value" -> cgtRef
        )
      ),
      "verifiers" -> Json.obj(
        "name" -> "cgtRef",
        "value" -> cgtRef
      )
    )

    val taxEnrolmentsSubscriberRequestBody = Json.obj(
      "serviceName" -> "CGT",
      "callbackUrl" -> TaxEnrolmentsKeys.callbackUrl,
      "etmpId" -> sap
    )

    for {
      enrolmentIssuerResponse <- taxEnrolmentsConnector.getIssuerResponse(cgtRef, taxEnrolmentsIssuerRequestBody)
      enrolmentSubscriberResponse <- taxEnrolmentsConnector.getSubscriberResponse(cgtRef, taxEnrolmentsSubscriberRequestBody)
    } yield (enrolmentIssuerResponse, enrolmentSubscriberResponse) match {
      case (SuccessTaxEnrolmentsResponse, SuccessTaxEnrolmentsResponse) => Future.successful((): Unit)
      case (_, _) => throw new Exception("Enrolling user for CGT failed")
    }
  }
}
