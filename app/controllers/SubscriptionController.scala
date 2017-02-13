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
import javax.inject.{Inject, Singleton}

import models.{ExceptionResponse, SubscriptionReferenceModel, UserFactsModel, CompanySubmissionModel}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import services.RegistrationSubscriptionService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class SubscriptionController @Inject()(actions: AuthorisedActions, registrationSubscriptionService: RegistrationSubscriptionService) extends BaseController {

  def subscribeKnownIndividual(nino: String): Action[AnyContent] = Action.async { implicit request =>
    Try(Nino(nino)) match {
      case Success(value) => actions.authorisedResidentIndividualAction {
        case true => authorisedKnownIndividualAction(value)
        case false => unauthorisedAction
      }
      case Failure(_) => badRequest
    }
  }

  def subscribeNonResidentNinoIndividual(nino: String): Action[AnyContent] = Action.async { implicit request =>
    Try(Nino(nino)) match {
      case Success(value) => actions.authorisedNonResidentIndividualAction {
        case true => authorisedKnownIndividualAction(value)
        case false => unauthorisedAction
      }
      case Failure(_) => badRequest
    }
  }

  def subscribeCompany(): Action[AnyContent] = Action.async { implicit request =>
    Try(request.body.asJson.get.as[CompanySubmissionModel]) match {
      case Success(value) if validOrganisationSubmission(value) => actions.authorisedOrganisationAction {
        case true => authorisedOrganisationIndividualAction(value)
        case false => unauthorisedAction
      }
      case _ => unauthorisedAction
    }
  }

  def subscribeGhostIndividual(): Action[AnyContent] = Action.async { implicit request =>
    Try(request.body.asJson.get.as[UserFactsModel]) match {
      case Success(value) => actions.authorisedNonResidentIndividualAction {
        case true => authorisedGhostIndividualAction(value)
        case false => unauthorisedAction
      }
      case Failure(_) => badRequest
    }
  }

  def authorisedKnownIndividualAction(nino: Nino)(implicit hc: HeaderCarrier): Future[Result] = {
    registrationSubscriptionService.subscribeKnownUser(nino.nino).map {
      reference => Ok(Json.toJson(SubscriptionReferenceModel(reference)))
    } recoverWith {
      case error => returnInternalServerError(error)
    }
  }

  def authorisedGhostIndividualAction(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier): Future[Result] = {
    registrationSubscriptionService.subscribeGhostUser(userFactsModel).map {
      reference => Ok(Json.toJson(SubscriptionReferenceModel(reference)))
    } recoverWith {
      case error => returnInternalServerError(error)
    }
  }

  def authorisedOrganisationIndividualAction(companySubmissionModel: CompanySubmissionModel)(implicit hc: HeaderCarrier): Future[Result] = {
    registrationSubscriptionService.subscribeOrganisationUser(companySubmissionModel).map {
      reference => Ok(Json.toJson(reference))
    } recoverWith {
      case error => returnInternalServerError(error)
    }
  }

  private def validOrganisationSubmission(model: CompanySubmissionModel): Boolean =
    model.sap.nonEmpty && model.contactAddress.nonEmpty && model.registeredAddress.nonEmpty

  private val unauthorisedAction: Future[Result] = Future.successful(Unauthorized(Json.toJson(ExceptionResponse(UNAUTHORIZED, "Unauthorised"))))
  private val badRequest: Future[Result] = Future.successful(BadRequest(Json.toJson(ExceptionResponse(BAD_REQUEST, "Bad Request"))))
  private def returnInternalServerError(error: Throwable): Future[Result] =
    Future.successful(InternalServerError(Json.toJson(ExceptionResponse(INTERNAL_SERVER_ERROR, error.getMessage))))
}
