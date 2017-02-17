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
import javax.inject.{Inject, Singleton}
import common.AuditConstants
import common.Keys.TaxEnrolmentsKeys
import config.{ApplicationConfig, WSHttp}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait TaxEnrolmentsResponse
case class SuccessTaxEnrolmentsResponse() extends TaxEnrolmentsResponse
case object TaxEnrolmentsErrorResponse extends TaxEnrolmentsResponse
case class InvalidTaxEnrolmentsRequest(message: String) extends TaxEnrolmentsResponse

@Singleton
class TaxEnrolmentsConnector @Inject()(appConfig: ApplicationConfig, logger: Logging) extends HttpErrorFunctions with ServicesConfig {

  lazy val serviceUrl: String = appConfig.baseUrl("tax-enrolments")
  lazy val serviceContext: String = appConfig.taxEnrolmentsContextUrl
  val http: HttpPut with HttpGet with HttpPost = WSHttp

  //TODO: move these into servicesConfig when received confirmation of content
  val urlHeaderEnvironment: String = ""
  val urlHeaderAuthorization: String = ""

  private[connectors] def customTaxEnrolmentsRead(http: String, url: String, response: HttpResponse) = {
    response.status match {
      case INTERNAL_SERVER_ERROR => throw new InternalServerException("Tax Enrolments returned an internal server error")
      case BAD_GATEWAY => throw new BadGatewayException("Tax Enrolments returned an upstream error")
      case _ => handleResponse(http, url)(response)
    }
  }

  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customTaxEnrolmentsRead(http, url, res)
  }

  private def createHeaderCarrier(headerCarrier: HeaderCarrier): HeaderCarrier = {
    headerCarrier.
      withExtraHeaders("Environment" -> urlHeaderEnvironment).
      copy(authorization = Some(Authorization(urlHeaderAuthorization)))
  }

  @inline
  private def cPUT[I, O](url: String, body: I)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier) =
    http.PUT[I, O](url, body)(wts = wts, rds = rds, hc = createHeaderCarrier(hc))

  def getIssuerResponse(subscriptionId: String, body: JsValue)(implicit hc: HeaderCarrier): Future[TaxEnrolmentsResponse] = {
    val putUrl = s"""$serviceUrl$serviceContext/subscriptions/$subscriptionId/${TaxEnrolmentsKeys.issuer}"""
    val response = cPUT(putUrl, body)
    val auditMap: Map[String, String] = Map("Subscription Id" -> subscriptionId, "Url" -> putUrl)

    Logger.warn(s"Made a put request to the tax enrolments issuer url: $putUrl")

    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.info(s"Successful Tax Enrolments issue to Url $putUrl")
          logger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuer,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessTaxEnrolmentsResponse()

        case BAD_REQUEST =>
          val message = (r.json \ "reason").as[String]
          Logger.warn(s"Tax Enrolments reported an error with the request $message to Url $putUrl")
          logger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuer,
            detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeFailure)
          InvalidTaxEnrolmentsRequest(message)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex, auditMap, AuditConstants.transactionTaxEnrolmentsIssuer)
    }
  }

  def getSubscriberResponse(subscriptionId: String, body: JsValue)(implicit headerCarrier: HeaderCarrier): Future[TaxEnrolmentsResponse] = {
    val putUrl = s"""$serviceUrl$serviceContext/subscriptions/$subscriptionId/${TaxEnrolmentsKeys.subscriber}"""
    val response = cPUT(putUrl, body)
    val auditMap: Map[String, String] = Map("Subscription Id" -> subscriptionId, "Url" -> putUrl)

    Logger.warn(s"Made a put request to the tax enrolments subscriber url: $putUrl")

    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.warn(s"Successful Tax Enrolments subscription to Url $putUrl")
          logger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsSubscribe,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessTaxEnrolmentsResponse()

        case BAD_REQUEST | UNAUTHORIZED =>
          val message = (r.json \ "reason").as[String]
          Logger.warn(s"Tax Enrolments reported an error with the request $message to Url $putUrl")
          logger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsSubscribe,
            detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeFailure)
          InvalidTaxEnrolmentsRequest(message)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex, auditMap, AuditConstants.transactionTaxEnrolmentsSubscribe)
    }
  }

  private[connectors] def recoverRequest(putUrl: String, ex: Throwable, auditMap: Map[String, String], auditTransactionName: String)
                                        (implicit headerCarrier: HeaderCarrier): TaxEnrolmentsResponse = {
    ex match {
      case _: InternalServerException =>
        Logger.warn(s"Tax Enrolments reported an internal server error status to Url $putUrl")
        logger.audit(transactionName = auditTransactionName,
          detail = auditMap,
          eventType = AuditConstants.eventTypeInternalServerError)
        TaxEnrolmentsErrorResponse

      case _: BadGatewayException =>
        Logger.warn(s"Tax Enrolments reported a bad gateway status to Url $putUrl")
        logger.audit(transactionName = auditTransactionName,
          detail = auditMap,
          eventType = AuditConstants.eventTypeBadGateway)
        TaxEnrolmentsErrorResponse

      case _: Exception =>
        Logger.warn(s"Tax Enrolments reported a ${ex.toString}")
        logger.audit(transactionName = auditTransactionName,
          detail = auditMap,
          eventType = AuditConstants.eventTypeGeneric)
        TaxEnrolmentsErrorResponse
    }
  }
}
