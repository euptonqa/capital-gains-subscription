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

import audit.Logging
import com.google.inject.{Inject, Singleton}
import common.AuditConstants
import config.{ApplicationConfig, WSHttp}
import models.{RegisterModel, SubscribeModel, SubscriptionRequest}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import uk.gov.hmrc.play.http._
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

sealed trait DesResponse
case class SuccessDesResponse(response: JsValue) extends DesResponse
case object NotFoundDesResponse extends DesResponse
case object DesErrorResponse extends DesResponse
case class InvalidDesRequest(message: String) extends DesResponse


@Singleton
class DESConnector @Inject()(appConfig: ApplicationConfig, logger: Logging) extends HttpErrorFunctions with ServicesConfig {

  lazy val serviceUrl: String = appConfig.baseUrl("des")
  lazy val serviceContext: String = appConfig.desContextUrl
  val environment = "test"
  val token = "des"
  val obtainBpUrl = "/register"

  val urlHeaderEnvironment = "??? see srcs, found in config"
  val urlHeaderAuthorization = "??? same as above"


  val http: HttpGet with HttpPost with HttpPut = WSHttp


  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse) = {
    response.status match {
      case BAD_REQUEST => response
      case NOT_FOUND => throw new NotFoundException("ETMP returned a Not Found status")
      case CONFLICT => response
      case INTERNAL_SERVER_ERROR => throw new InternalServerException("ETMP returned an internal server error")
      case BAD_GATEWAY => throw new BadGatewayException("ETMP returned an upstream error")
      case _ => handleResponse(http, url)(response)
    }
  }


  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customDESRead(http, url, res)
  }


  def subscribe(subscribeModel: SubscribeModel, subscribeRequest: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[DesResponse] = {
    val requestUrl: String = s"$serviceUrl$serviceContext/individual/${subscribeModel.sap}/subscribe"
    val response = cPOST(requestUrl, Json.toJson(subscribeModel))
    val auditMap: Map[String, String] = Map("Safe Id" -> subscribeModel.sap, "Url" -> requestUrl)

    val ackReq = subscribeRequest.acknowledgementReference
    response map { r =>
      r.status match {
        case OK =>
          Logger.info(s"Successful DES submission for $ackReq")
          logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessDesResponse(r.json
          )
        case CONFLICT =>
          Logger.warn(s"Duplicate submission for $ackReq has been reported")
          logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
            detail = auditMap ++ Map("Conflict reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeConflict)
          SuccessDesResponse(r.json
          )
        case ACCEPTED =>
          Logger.info(s"Accepted DES submission for $ackReq")
          logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessDesResponse(r.json
          )
        case BAD_REQUEST =>
          val message = (r.json \ "reason").as[String]
          Logger.warn(s"Error with the request $message")
          logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
            detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeFailure)
          InvalidDesRequest(message)
      }
    } recover {
      case ex: NotFoundException =>
        Logger.warn(s"Not found for $ackReq")
        logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
          detail = auditMap,
          eventType = AuditConstants.eventTypeNotFound)
        NotFoundDesResponse
      case ex: InternalServerException =>
        Logger.warn(s"Internal server error for $ackReq")
        logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
          detail = auditMap,
          eventType = AuditConstants.eventTypeInternalServerError)
        DesErrorResponse
      case ex: BadGatewayException =>
        Logger.warn(s"Bad gateway status for $ackReq")
        logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
          detail = auditMap,
          eventType = AuditConstants.eventTypeBadGateway)
        DesErrorResponse
      case ex: Exception =>
        Logger.warn(s"Exception of ${ex.toString} for $ackReq")
        logger.audit(transactionName = AuditConstants.transactionDESSubscribe,
          detail = auditMap,
          eventType = AuditConstants.eventTypeGeneric)
        DesErrorResponse
    }
  }

  def obtainBp(registerModel: RegisterModel)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesResponse] = {
    val requestUrl = s"$serviceUrl$serviceContext/individual/${registerModel.nino}$obtainBpUrl"
    val jsonNino = Json.toJson(registerModel)
    val response = cPOST(requestUrl, jsonNino)
    val auditMap: Map[String, String] = Map("Nino" -> registerModel.nino.nino, "Url" -> requestUrl)

    response map {
      r =>
        r.status match {
          case OK =>
            Logger.info("Successful DES request for BP number")
            logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
              detail = auditMap,
              eventType = AuditConstants.eventTypeSuccess)
            SuccessDesResponse(r.json)
          case ACCEPTED =>
            Logger.info("Accepted DES request for BP number")
            logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
              detail = auditMap,
              eventType = AuditConstants.eventTypeSuccess)
            SuccessDesResponse(r.json
            )
          case CONFLICT =>
            Logger.info("Conflicted DES request for BP number - BP Number already in existence")
            logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
              detail = auditMap ++ Map("Conflict reason" -> r.body, "Status" -> r.status.toString),
              eventType = AuditConstants.eventTypeConflict)
            SuccessDesResponse(r.json
            )
          case BAD_REQUEST =>
            val message = (r.json \ "reason").as[String]
            Logger.warn(s"Error with the request $message")
            logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
              detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
              eventType = AuditConstants.eventTypeFailure)
            InvalidDesRequest(message)
        }
    } recover {
      case ex: NotFoundException =>
        Logger.warn("Not found exception for DES request for BP number")
        logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
          detail = auditMap,
          eventType = AuditConstants.eventTypeNotFound)
        NotFoundDesResponse
      case ex: InternalServerException =>
        Logger.warn("Internal server error for DES request for BP number")
        logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
          detail = auditMap,
          eventType = AuditConstants.eventTypeInternalServerError)
        DesErrorResponse
      case ex: BadGatewayException =>
        Logger.warn("Bad gateway status for DES request for BP number")
        logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
          detail = auditMap,
          eventType = AuditConstants.eventTypeBadGateway)
        DesErrorResponse
      case ex: Exception =>
        Logger.warn(s"Exception of ${ex.toString} for DES request for BP number")
        logger.audit(transactionName = AuditConstants.transactionDESObtainBP,
          detail = auditMap,
          eventType = AuditConstants.eventTypeGeneric)
        DesErrorResponse
    }

  }

    private def createHeaderCarrier(headerCarrier: HeaderCarrier): HeaderCarrier = {
      headerCarrier.
        withExtraHeaders("Environment" -> urlHeaderEnvironment).
        copy(authorization = Some(Authorization(urlHeaderAuthorization)))
    }

    @inline
    private def cPOST[I, O](url: String, body: I, headers: Seq[(String, String)] = Seq.empty)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier) =
      http.POST[I, O](url, body, headers)(wts = wts, rds = rds, hc = createHeaderCarrier(hc))

}
