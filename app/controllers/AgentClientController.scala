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

import javax.inject.{Inject, Singleton}

import auth.AuthorisedActions
import models.{ExceptionResponse, UserFactsModel}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import services.RegistrationSubscriptionService
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class AgentClientController @Inject()(actions: AuthorisedActions,
                                      registrationSubscriptionService: RegistrationSubscriptionService) extends BaseController {

  //Duplicated in Subscription Controller - refactor into a common responses object?
  private val unauthorisedAction: Future[Result] = Future.successful(Unauthorized(Json.toJson(ExceptionResponse(UNAUTHORIZED, "Unauthorised"))))
  private val badRequest: Future[Result] = Future.successful(BadRequest(Json.toJson(ExceptionResponse(BAD_REQUEST, "Bad Request"))))

  private def returnInternalServerError(error: Throwable): Future[Result] =
    Future.successful(InternalServerError(Json.toJson(ExceptionResponse(INTERNAL_SERVER_ERROR, error.getMessage))))

  val subscribeIndividual: Action[AnyContent] = Action.async { implicit request =>
    Try(request.body.asJson.get.as[UserFactsModel]) match {
      case Success(value) => actions.authorisedAgentAction {
        case true => authorisedAgentAction(value)
        case false => unauthorisedAction
      }
      case Failure(_) => badRequest
    }
  }

  //Duplicated in Subscription Controller - refactor into a common actions object?
  private def authorisedAgentAction(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier): Future[Result] = {
    registrationSubscriptionService.subscribeGhostUser(userFactsModel).map { _ =>
      NoContent
    } recoverWith {
      case error => returnInternalServerError(error)
    }
  }
}