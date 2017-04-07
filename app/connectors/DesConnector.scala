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
import common.AuditConstants.{transactionDESRegisterKnownUser, _}
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
import scala.concurrent.{ExecutionContext, Future}

sealed trait DesResponse

case class SuccessfulRegistrationResponse(response: RegisterUserModel) extends DesResponse

case class DesErrorResponse(message: JsValue) extends DesResponse

case object DuplicateDesResponse extends DesResponse

@Singleton
class DesConnector @Inject()(appConfig: ApplicationConfig, logger: Logging) extends HttpErrorFunctions with ServicesConfig {

  lazy val serviceUrl: String = appConfig.baseUrl("des")
  lazy val serviceContext: String = appConfig.desContextUrl
  lazy val environment: String = appConfig.desEnvironment
  lazy val token: String = appConfig.desToken

  val http: HttpGet with HttpPost with HttpPut = WSHttp

  //TODO: This implicit reads and the customDESRead are largely redundant and I will refactor them later.
  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse): HttpResponse = customDESRead(http, url, res)
  }
  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse) = {
    //This function is called from the HTTPErroFunctions of the hmrcHttp Lib
    handleResponse(http, url)(response)
  }

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
  private def logAndAuditHttpResponse(transactionIdentifier: String, messageToLog: String, auditDetails: Map[String, String], eventType: String) = {
    Logger.warn(messageToLog)
    logger.audit(transactionIdentifier, auditDetails, eventType)
  }

  def handleRegistrationErrorResponse(response: HttpResponse, transactionId: String, auditDetails: Map[String, String]): DesResponse = response.status match {
    case CONFLICT =>
      logAndAuditHttpResponse(transactionId, "Duplicate BP found", conflictAuditMap(auditDetails, response), eventTypeConflict)
      DuplicateDesResponse
    case errorStatus =>
      logAndAuditHttpResponse(transactionId, s"Registration failed - error code: $errorStatus body: ${response.body}",
        failureAuditMap(auditDetails, response), eventTypeFailure)
      DesErrorResponse(response.json)
  }

  def registerIndividualWithNino(model: RegisterIndividualModel)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesResponse] = {

    //TODO: abstract part of this to app-config
    val requestUrl = s"$serviceUrl$serviceContext/registration/individual/nino/${model.nino.nino}"

    val registerRequestBody = Json.obj(
      "regime" -> Keys.DESKeys.cgtRegime,
      "requiresNameMatch" -> false,
      "isAnAgent" -> false)
    val response = cPOST(requestUrl, registerRequestBody)
    val auditDetails: Map[String, String] = Map("RequestBody" -> registerRequestBody.toString(), "Url" -> requestUrl)

    Logger.info("Made a post request to the stub with a url of " + requestUrl)

    response map {
      r =>
        r.status match {
          case OK =>
            logAndAuditHttpResponse(transactionDESRegisterKnownUser, "Successful registration of known user", auditDetails, eventTypeSuccess)
            SuccessfulRegistrationResponse((r.json \ "safeId").as[RegisterUserModel])
          case _ =>
            handleRegistrationErrorResponse(r, transactionDESRegisterKnownUser, auditDetails)
        }
    }
  }

  def registerIndividualGhost(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesResponse] = {

    //TODO: Abstract this to app-config
    val registerGhostUrl = "/non-resident/individual/register"

    val requestUrl: String = s"$serviceUrl$serviceContext$registerGhostUrl"
    val jsonFullDetails = Json.toJson(userFactsModel)
    val response = http.POST(requestUrl, jsonFullDetails)
    val auditDetails: Map[String, String] = Map("Full details" -> userFactsModel.toString, "Url" -> requestUrl)

    Logger.info("Made a post request to the stub with a url of " + requestUrl)

    response map {
      r =>
        r.status match {
          case OK =>
            logAndAuditHttpResponse(transactionDESRegisterGhost, "Successful registration of ghost user", auditDetails, eventTypeSuccess)
            SuccessfulRegistrationResponse((r.json \ "safeId").as[RegisterUserModel])
          case _ => handleRegistrationErrorResponse(r, transactionDESRegisterGhost, auditDetails)
    }
  }

  //TODO: review this entire call as there doesn't seem to be an endpoint for it at the moment and team ITSA have discovered it's never triggered.
  def getSAPForExistingBP(model: RegisterIndividualModel)(implicit hc: HeaderCarrier): Future[DesResponse] = {
    val getSubscriptionUrl = s"$serviceUrl$serviceContext/registration/individual/nino/${model.nino.nino}"
    val response = cGET[HttpResponse](getSubscriptionUrl, Seq(("nino", model.nino.nino)))
    val auditDetails: Map[String, String] = Map("Nino" -> model.nino.nino, "Url" -> getSubscriptionUrl)
    Logger.info("Made a post request to the stub with a url of " + getSubscriptionUrl)

    response map {
      r =>
        r.status match {
          case OK => logAndAuditHttpResponse(transactionDESGetExistingSAP, "Successful request for existing SAP", auditDetails, eventTypeSuccess)
            SuccessfulRegistrationResponse((r.json \ "safeId").as[RegisterUserModel])
          case errorStatus =>
            logAndAuditHttpResponse(
              transactionDESGetExistingSAP, s"Retrieve exisitng BP failed - error code: $errorStatus body: ${r.body}",
              failureAuditMap(auditDetails, r), eventTypeFailure)
            DesErrorResponse(r.json)
        }
    }
  }

  //TODO AWAITING SUBSCRIPTION API SPECS FOR GOD'S SAKE
  def subscribe(submissionModel: Any)(implicit hc: HeaderCarrier): Future[DesResponse] = {

    submissionModel match {
      case individual: SubscribeIndividualModel =>
        Logger.info("Made a post request to the stub with an individual subscribers sap of " + individual.sap)

        val requestUrl: String = s"$serviceUrl$serviceContext/individual/${individual.sap}/subscribe"
        val response = cPOST(requestUrl, Json.toJson(individual))
        val auditDetails: Map[String, String] = Map("Safe Id" -> individual.sap, "Url" -> requestUrl)
        handleSubscriptionForCGTResponse(response, auditDetails, individual.sap)

      case company: CompanySubmissionModel =>
        Logger.info("Made a post request to the stub with a company subscribers sap of " + company.sap.get)

        val requestUrl: String = s"$serviceUrl$serviceContext/non-resident/organisation/subscribe"
        val response = cPOST(requestUrl, Json.toJson(company))
        val auditDetails: Map[String, String] = Map("Safe Id" -> company.sap.get, "Url" -> requestUrl)
        handleSubscriptionForCGTResponse(response, auditDetails, company.sap.get)
    }
  }

  def handleSubscriptionForCGTResponse(response: Future[HttpResponse], auditDetails: Map[String, String], reference: String)
                                      (implicit hc: HeaderCarrier): Future[DesResponse] = {
    response map { r =>
      r.status match {
        case OK =>
          Logger.info(s"Successful DES submission for $reference")
          logger.audit(transactionDESSubscribe, auditDetails, eventTypeSuccess)
          SuccessDesResponse(r.json)
        case CONFLICT =>
          Logger.warn("Error Conflict: SAP Number already in existence")
          logger.audit(transactionDESSubscribe, conflictAuditMap(auditDetails, r), eventTypeConflict)
          DuplicateDesResponse
        case ACCEPTED =>
          Logger.info(s"Accepted DES submission for $reference")
          logger.audit(transactionDESSubscribe, auditDetails, eventTypeSuccess)
          SuccessDesResponse(r.json)
        case BAD_REQUEST =>
          Logger.warn(s"Error with the request ${r.body}")
          logger.audit(transactionDESSubscribe, failureAuditMap(auditDetails, r), eventTypeFailure)
          InvalidDesRequest(r.json)
      }
    } recover {
      case _: NotFoundException =>
        Logger.warn(s"Not found for $reference")
        logger.audit(transactionDESSubscribe, auditDetails, eventTypeNotFound)
        NotFoundDesResponse
      case _: InternalServerException =>
        Logger.warn(s"Internal server error for $reference")
        logger.audit(transactionDESSubscribe, auditDetails, eventTypeInternalServerError)
        DesErrorResponse
      case _: BadGatewayException =>
        Logger.warn(s"Bad gateway status for $reference")
        logger.audit(transactionDESSubscribe, auditDetails, eventTypeBadGateway)
        DesErrorResponse
      case ex: Exception =>
        Logger.warn(s"Exception of ${ex.printStackTrace()} for $reference")
        logger.audit(transactionDESSubscribe, auditDetails, eventTypeGeneric)
        DesErrorResponse
    }
  }
}
