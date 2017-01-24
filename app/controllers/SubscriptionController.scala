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
import com.google.inject.{Inject, Singleton}
import models.ExceptionResponse
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

@Singleton
class SubscriptionController @Inject()(actions: AuthorisedActions) extends BaseController {

  //TODO replace stubbed method with injected service
  def subscribeUser(): Future[String] = Future.successful("CGT123456")

  def subscribeResidentIndividual(nino: String): Action[AnyContent] = Action.async { implicit request =>

    Try(Nino(nino)) match {
      case Success(value) => actions.authorisedResidentIndividualAction {
        case true => subscribeUser().map {
          case reference => Ok(Json.toJson(reference))
        } recoverWith {
          case error => Future.successful(InternalServerError(Json.toJson(ExceptionResponse(INTERNAL_SERVER_ERROR, error.getMessage))))
        }
        case _ => Future.successful(Unauthorized(Json.toJson(ExceptionResponse(UNAUTHORIZED, "Unauthorised"))))
      }
      case _ => Future.successful(Unauthorized(Json.toJson(ExceptionResponse(UNAUTHORIZED, "Unauthorised"))))
    }
  }
}
