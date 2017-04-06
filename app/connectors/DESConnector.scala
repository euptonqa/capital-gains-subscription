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
class DESConnector @Inject()(appConfig: ApplicationConfig, logger: Logging) extends HttpErrorFunctions with ServicesConfig {

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

  private def conflictAuditMap(auditMap: Map[String, String], response: HttpResponse) =
    auditMap ++ Map("Conflict reason" -> response.body, "Status" -> response.status.toString)

  private def failureAuditMap(auditMap: Map[String, String], response: HttpResponse) =
    auditMap ++ Map("Failure reason" -> response.body, "Status" -> response.status.toString)

  //Feels dirty but this is a function called purely for it's side effects...
  private def logAndAuditHttpResponse(transactionIdentifier: String, messageToLog: String, auditMap: Map[String, String], eventType: String) = {
    Logger.warn(messageToLog)
    logger.audit(transactionIdentifier, auditMap, eventType)
  }

  def handleRegistrationResponse(response: HttpResponse, transactionId: String, auditMap: Map[String, String]): DesResponse = response.status match {
    case OK =>
      logAndAuditHttpResponse(transactionId, "Successful registration of known user", auditMap, eventTypeSuccess)
      SuccessfulRegistrationResponse((response.json \ "safeId").as[RegisterUserModel])
    case CONFLICT =>
      logAndAuditHttpResponse(transactionId, "Duplicate BP found", conflictAuditMap(auditMap, response), eventTypeConflict)
      DuplicateDesResponse
    case errorStatus =>
      logAndAuditHttpResponse(transactionId, s"Registration failed - error code: $errorStatus body: ${response.body}",
        failureAuditMap(auditMap, response), eventTypeFailure)
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
    val auditMap: Map[String, String] = Map("Nino" -> model.nino.nino, "Url" -> requestUrl)

    Logger.info("Made a post request to the stub with a url of " + requestUrl)

    response map {
      r => handleRegistrationResponse(r, transactionDESRegisterKnownUser, auditMap)
    }
  }

  def registerIndividualGhost(userFactsModel: UserFactsModel)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesResponse] = {

    //TODO: Abstract this to app-config
    val registerGhostUrl = "/non-resident/individual/register"

    val requestUrl: String = s"$serviceUrl$serviceContext$registerGhostUrl"
    val jsonFullDetails = Json.toJson(userFactsModel)
    val response = http.POST(requestUrl, jsonFullDetails)
    val auditMap: Map[String, String] = Map("Full details" -> userFactsModel.toString, "Url" -> requestUrl)

    Logger.info("Made a post request to the stub with a url of " + requestUrl)

    response map {
      r => handleRegistrationResponse(r, transactionDESRegisterGhost, auditMap)
    }
  }

  //TODO: review this entire call as there doesn't seem to be an endpoint for it at the moment and team ITSA have discovered it's never triggered.
  def getSAPForExistingBP(model: RegisterIndividualModel)(implicit hc: HeaderCarrier): Future[DesResponse] = {
    val getSubscriptionUrl = s"$serviceUrl$serviceContext/registration/individual/nino/${model.nino.nino}"
    val response = cGET[HttpResponse](getSubscriptionUrl, Seq(("nino", model.nino.nino)))
    val auditMap: Map[String, String] = Map("Nino" -> model.nino.nino, "Url" -> getSubscriptionUrl)
    Logger.info("Made a post request to the stub with a url of " + getSubscriptionUrl)

    response map {
      r =>
        r.status match {
          case OK => logAndAuditHttpResponse(transactionDESGetExistingSAP, "Successful request for existing SAP", auditMap, eventTypeSuccess)
            SuccessfulRegistrationResponse((r.json \ "safeId").as[RegisterUserModel])
          case errorStatus =>
            logAndAuditHttpResponse(
              transactionDESGetExistingSAP, s"Retrieve exisitng BP failed - error code: $errorStatus body: ${r.body}",
              failureAuditMap(auditMap, r), eventTypeFailure)
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
        val auditMap: Map[String, String] = Map("Safe Id" -> individual.sap, "Url" -> requestUrl)
        handleSubscriptionForCGTResponse(response, auditMap, individual.sap)

      case company: CompanySubmissionModel =>
        Logger.info("Made a post request to the stub with a company subscribers sap of " + company.sap.get)

        val requestUrl: String = s"$serviceUrl$serviceContext/non-resident/organisation/subscribe"
        val response = cPOST(requestUrl, Json.toJson(company))
        val auditMap: Map[String, String] = Map("Safe Id" -> company.sap.get, "Url" -> requestUrl)
        handleSubscriptionForCGTResponse(response, auditMap, company.sap.get)
    }
  }

  def handleSubscriptionForCGTResponse(response: Future[HttpResponse], auditMap: Map[String, String], reference: String)
                                      (implicit hc: HeaderCarrier): Future[DesResponse] = {
    response map { r =>
      r.status match {
        case OK =>
          Logger.info(s"Successful DES submission for $reference")
          logger.audit(transactionDESSubscribe, auditMap, eventTypeSuccess)
          SuccessDesResponse(r.json)
        case CONFLICT =>
          Logger.warn("Error Conflict: SAP Number already in existence")
          logger.audit(transactionDESSubscribe, conflictAuditMap(auditMap, r), eventTypeConflict)
          DuplicateDesResponse
        case ACCEPTED =>
          Logger.info(s"Accepted DES submission for $reference")
          logger.audit(transactionDESSubscribe, auditMap, eventTypeSuccess)
          SuccessDesResponse(r.json)
        case BAD_REQUEST =>
          Logger.warn(s"Error with the request ${r.body}")
          logger.audit(transactionDESSubscribe, failureAuditMap(auditMap, r), eventTypeFailure)
          InvalidDesRequest(r.json)
      }
    } recover {
      case _: NotFoundException =>
        Logger.warn(s"Not found for $reference")
        logger.audit(transactionDESSubscribe, auditMap, eventTypeNotFound)
        NotFoundDesResponse
      case _: InternalServerException =>
        Logger.warn(s"Internal server error for $reference")
        logger.audit(transactionDESSubscribe, auditMap, eventTypeInternalServerError)
        DesErrorResponse
      case _: BadGatewayException =>
        Logger.warn(s"Bad gateway status for $reference")
        logger.audit(transactionDESSubscribe, auditMap, eventTypeBadGateway)
        DesErrorResponse
      case ex: Exception =>
        Logger.warn(s"Exception of ${ex.printStackTrace()} for $reference")
        logger.audit(transactionDESSubscribe, auditMap, eventTypeGeneric)
        DesErrorResponse
    }
  }
}
