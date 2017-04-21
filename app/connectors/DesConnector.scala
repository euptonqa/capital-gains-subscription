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

package connectors

import javax.inject.{Inject, Singleton}

import audit.Logging
import common.AuditConstants._
import common.Keys
import config.{ApplicationConfig, WSHttp}
import models._
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait DesResponse

case class SuccessfulRegistrationResponse(response: RegisteredUserModel) extends DesResponse

case class SuccessfulSubscriptionResponse(response: SubscriptionReferenceModel) extends DesResponse

//Not sure if this would be better responding with an error model that holds the status code and the message?
case class DesErrorResponse(message: String) extends DesResponse

case object DuplicateDesResponse extends DesResponse

@Singleton
class DesConnector @Inject()(appConfig: ApplicationConfig, logger: Logging) extends HttpErrorFunctions with ServicesConfig {

  lazy val serviceUrl: String = appConfig.baseUrl("des")
  lazy val serviceContext: String = appConfig.desContextUrl
  lazy val environment: String = appConfig.desEnvironment
  lazy val token: String = appConfig.desToken

  val http: HttpGet with HttpPost with HttpPut = WSHttp

  //TODO: This implicit reads and the customDESRead are largely redundant and can be refactored out.
  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse): HttpResponse = customDESRead(http, url, res)
  }

  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse) = {
    //This function is called from the HTTPErrorFunctions of the hmrcHttp Lib
    handleResponse(http, url)(response)
  }

  //TODO refactor these custom posts HTTP's
  @inline
  private def cPOST[I, O](url: String, body: I, headers: Seq[(String, String)] = Seq.empty)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier) = {
    http.POST[I, O](url, body, headers)(wts = wts, rds = rds, hc = attachDesAuthorisationToHeaderCarrier(hc))
  }

  @inline
  private def cGET[O](url: String, queryParams: Seq[(String, String)] = Seq.empty)(implicit rds: HttpReads[O], hc: HeaderCarrier) = {
    http.GET[O](url, queryParams)(rds, hc = attachDesAuthorisationToHeaderCarrier(hc))
  }

  private def attachDesAuthorisationToHeaderCarrier(headerCarrier: HeaderCarrier): HeaderCarrier = {
    headerCarrier.copy(authorization = Some(Authorization(s"Bearer $token"))).withExtraHeaders("Environment" -> environment)
  }

  private def conflictAuditMap(auditDetails: Map[String, String], response: HttpResponse) =
    auditDetails ++ Map("Conflict reason" -> response.body, "Status" -> response.status.toString)

  private def failureAuditMap(auditDetails: Map[String, String], response: HttpResponse) =
    auditDetails ++ Map("Failure reason" -> response.body, "Status" -> response.status.toString)

  //Feels dirty but this is a function called purely for it's side effects...
  private def logAndAuditHttpResponse(transactionIdentifier: String,
                                      messageToLog: String,
                                      auditDetails: Map[String, String],
                                      eventType: String)(implicit hc: HeaderCarrier) = {
    Logger.warn(messageToLog)
    logger.audit(transactionIdentifier, auditDetails, eventType)
  }

  def registerIndividualWithNino(model: RegisterIndividualModel)(implicit hc: HeaderCarrier): Future[DesResponse] = {

    //TODO: abstract part of this to app-config
    val requestUrl = s"$serviceUrl$serviceContext/registration/individual/nino/${model.nino.nino}"

    val registerRequestBody = Json.obj(
      "regime" -> Keys.DESKeys.cgtRegime,
      "requiresNameMatch" -> false,
      "isAnAgent" -> false)
    val response = cPOST(requestUrl, registerRequestBody)
    val auditDetails: Map[String, String] = Map("RequestBody" -> registerRequestBody.toString(), "Url" -> requestUrl)

    Logger.info("Made a post request to the register individual with nino with a url of " + requestUrl)

    response map {
      r =>
        r.status match {
          case OK =>
            logAndAuditHttpResponse(transactionDESRegisterKnownUser, "Successful registration of known user", auditDetails, eventTypeSuccess)
            //This parsing here could be done like --> r.json.as[RegisteredUserModel] but I don't think it is as clear what actually happens there.
            SuccessfulRegistrationResponse(RegisteredUserModel((r.json \ "safeId").as[String]))
          case CONFLICT =>
            logAndAuditHttpResponse(transactionDESRegisterKnownUser, "Duplicate BP found", conflictAuditMap(auditDetails, r), eventTypeConflict)
            DuplicateDesResponse
          case errorStatus =>
            logAndAuditHttpResponse(transactionDESRegisterKnownUser, s"Registration failed - error code: $errorStatus body: ${r.body}",
              failureAuditMap(auditDetails, r), eventTypeFailure)
            DesErrorResponse((r.json \ "reason").as[String])
        }
    }
  }

  def registerIndividualGhost(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier): Future[DesResponse] = {

    //TODO: Abstract this to app-config
    val registerGhostUrl = "/non-resident/individual/register"

    val requestUrl: String = s"$serviceUrl$serviceContext$registerGhostUrl"
    val response = cPOST[JsValue, HttpResponse](requestUrl, userFactsModel.asRegistrationPayload)
    val auditDetails: Map[String, String] = Map("Full details" -> userFactsModel.toString, "Url" -> requestUrl)

    Logger.info("Made a post request to the stub with a url of " + requestUrl)

    response map {
      r =>
        r.status match {
          case OK =>
            logAndAuditHttpResponse(transactionDESRegisterGhost, "Successful registration of ghost user", auditDetails, eventTypeSuccess)
            SuccessfulRegistrationResponse(RegisteredUserModel((r.json \ "safeId").as[String]))
          case errorStatus =>
            logAndAuditHttpResponse(transactionDESRegisterGhost, s"Registration failed - error code: $errorStatus body: ${r.body}",
              failureAuditMap(auditDetails, r), eventTypeFailure)
            DesErrorResponse((r.json \ "reason").as[String])
        }
    }
  }

  //TODO: review this entire call as there doesn't seem to be an endpoint for it at the moment and team ITSA have discovered it's never triggered.
  def getSAPForExistingBP(model: RegisterIndividualModel)(implicit hc: HeaderCarrier): Future[DesResponse] = {

    //TODO: Abstract this to app-config
    val getSubscriptionUrl = s"$serviceUrl$serviceContext/registration/individual/nino/${model.nino.nino}"

    val response = cGET[HttpResponse](getSubscriptionUrl, Seq(("nino", model.nino.nino)))
    val auditDetails: Map[String, String] = Map("Nino" -> model.nino.nino, "Url" -> getSubscriptionUrl)
    Logger.info("Made a post request to the stub with a url of " + getSubscriptionUrl)

    response map {
      r =>
        r.status match {
          case OK => logAndAuditHttpResponse(transactionDESGetExistingSAP, "Successful request for existing SAP", auditDetails, eventTypeSuccess)
            SuccessfulRegistrationResponse(RegisteredUserModel((r.json \ "safeId").as[String]))
          case errorStatus =>
            logAndAuditHttpResponse(
              transactionDESGetExistingSAP, s"Retrieve existing BP failed - error code: $errorStatus body: ${r.body}",
              failureAuditMap(auditDetails, r), eventTypeFailure)
            DesErrorResponse((r.json \ "reason").as[String])
        }
    }
  }

  def subscribeIndividualForCgt(subscribeIndividualModel: SubscribeIndividualModel)(implicit hc: HeaderCarrier): Future[DesResponse] = {

    Logger.info("Made a post request to the stub with an individual subscribers sap of " + subscribeIndividualModel.sap)

    //TODO: Abstract this to app-config
    val requestUrl: String = s"$serviceUrl$serviceContext/create/${subscribeIndividualModel.sap}/subscription"

    //TODO update the body of this subscription request

    val response = cPOST(requestUrl, Json.toJson(subscribeIndividualModel))
    val auditDetails: Map[String, String] = Map("Safe Id" -> subscribeIndividualModel.sap, "Url" -> requestUrl)

    response map { r =>
      r.status match {
        case OK =>
          logAndAuditHttpResponse(transactionDESSubscribe, "Successful DES submission for $reference", auditDetails, eventTypeSuccess)
          SuccessfulSubscriptionResponse(SubscriptionReferenceModel((r.json \ "subscriptionCGT" \ "referenceNumber").as[String]))
        case errorStatus =>
          logAndAuditHttpResponse(transactionDESSubscribe, s"Subscription failed - error code: $errorStatus body: ${r.body}",
            failureAuditMap(auditDetails, r), eventTypeFailure)
          DesErrorResponse((r.json \ "reason").as[String])
      }
    }
  }

  def subscribeCompanyForCgt(companySubmissionModel: CompanySubmissionModel)(implicit hc: HeaderCarrier): Future[DesResponse] = {

    Logger.info("Made a post request to the stub with a company subscribers sap of " + companySubmissionModel.sap)

    //TODO: Abstract this to app-config
    val requestUrl: String = s"$serviceUrl$serviceContext/create/${companySubmissionModel.sap}/subscription"

    val response = cPOST[JsValue, HttpResponse](requestUrl, companySubmissionModel.toSubscriptionPayload)
    val auditDetails: Map[String, String] = Map("Safe Id" -> companySubmissionModel.sap.get, "Url" -> requestUrl)

    response map { r =>
      r.status match {
        case OK =>
          logAndAuditHttpResponse(transactionDESSubscribe, "Successful DES submission for $reference", auditDetails, eventTypeSuccess)
          SuccessfulSubscriptionResponse(SubscriptionReferenceModel((r.json \ "subscriptionCGT" \ "referenceNumber").as[String]))
        case errorStatus =>
          logAndAuditHttpResponse(transactionDESSubscribe, s"Subscription failed - error code: $errorStatus body: ${r.body}",
            failureAuditMap(auditDetails, r), eventTypeFailure)
          DesErrorResponse((r.json \ "reason").as[String])
      }
    }
  }
}
