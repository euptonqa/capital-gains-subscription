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
import common.AuditConstants
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

case object SuccessTaxEnrolmentsResponse extends TaxEnrolmentsResponse

case object TaxEnrolmentsErrorResponse extends TaxEnrolmentsResponse

//TODO: check reasoning for having this response at all, we already log the type and only care about 2 different responses so why?
case class InvalidTaxEnrolmentsRequest(message: JsValue) extends TaxEnrolmentsResponse

@Singleton
class TaxEnrolmentsConnector @Inject()(appConfig: ApplicationConfig, auditLogger: Logging) extends HttpErrorFunctions with ServicesConfig {

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
    def read(http: String, url: String, res: HttpResponse): HttpResponse = customTaxEnrolmentsRead(http, url, res)
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
    val putUrl = s"""$serviceUrl$serviceContext/subscriptions/$subscriptionId/issuer"""
    val response = cPUT(putUrl, body)
    val auditMap: Map[String, String] = Map("Subscription Id" -> subscriptionId, "Url" -> putUrl)

    Logger.info(s"Made a put request to the tax enrolments issuer url: $putUrl")

    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.info(s"Successful Tax Enrolments issue to Url $putUrl")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuer,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessTaxEnrolmentsResponse

        case BAD_REQUEST =>
          Logger.warn(s"Tax Enrolments reported an error with the request ${r.body} to Url $putUrl")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuer,
            detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeFailure)
          InvalidTaxEnrolmentsRequest(r.json)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex, auditMap, AuditConstants.transactionTaxEnrolmentsIssuer)
    }
  }

  def getSubscriberResponse(subscriptionId: String, body: JsValue)(implicit headerCarrier: HeaderCarrier): Future[TaxEnrolmentsResponse] = {
    val putUrl = s"""$serviceUrl$serviceContext/subscriptions/$subscriptionId/subscriber"""
    val response = cPUT(putUrl, body)
    val auditMap: Map[String, String] = Map("Subscription Id" -> subscriptionId, "Url" -> putUrl)

    Logger.info(s"Made a put request to the tax enrolments subscriber url: $putUrl")

    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.info(s"Successful Tax Enrolments subscription to Url $putUrl")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsSubscribe,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessTaxEnrolmentsResponse
        case BAD_REQUEST | UNAUTHORIZED =>
          Logger.warn(s"Tax Enrolments reported an error with the request ${r.body} to Url $putUrl")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsSubscribe,
            detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeFailure)
          InvalidTaxEnrolmentsRequest(r.json)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex, auditMap, AuditConstants.transactionTaxEnrolmentsSubscribe)
    }
  }

  def getSubscriberAgentResponse(arn: String, body: JsValue)(implicit headerCarrier: HeaderCarrier): Future[TaxEnrolmentsResponse] = {
    val putUrl = s"""$serviceUrl$serviceContext/subscriptions/$arn/subscriber"""
    val response = cPUT(putUrl, body)
    val auditMap: Map[String, String] = Map("Agent Reference Number" -> arn, "Url" -> putUrl)

    Logger.info("Made a PUT request to the tax enrolments subscriber as an Agent")

    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.info(s"Successful call to Tax Enrolments subscriber for agent")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuerAgent,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessTaxEnrolmentsResponse
        case BAD_REQUEST | UNAUTHORIZED =>
          Logger.warn(s"Failed call to Tax Enrolments subscribing an agent with reason ${r.body}.")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuerAgent,
            detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeFailure)
          InvalidTaxEnrolmentsRequest(r.json)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex, auditMap, AuditConstants.transactionTaxEnrolmentsSubscribe)
    }
  }

  def getIssuerAgentResponse(arn: String, body: JsValue)(implicit hc: HeaderCarrier): Future[TaxEnrolmentsResponse] = {
    val putUrl = s"""$serviceUrl$serviceContext/subscriptions/$arn/issuer"""
    val response = cPUT(putUrl, body)
    val auditMap: Map[String, String] = Map("Agent Reference Number" -> arn, "Url" -> putUrl)

    Logger.info("Made a PUT request to the tax enrolments issuer stub with an ARN of" + arn)

    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.info(s"Successful Tax Enrolments issue to Url $putUrl")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuer,
            detail = auditMap,
            eventType = AuditConstants.eventTypeSuccess)
          SuccessTaxEnrolmentsResponse
        case BAD_REQUEST =>
          Logger.warn(s"Tax Enrolments reported an error with the request ${r.body} to Url $putUrl")
          auditLogger.audit(transactionName = AuditConstants.transactionTaxEnrolmentsIssuer,
            detail = auditMap ++ Map("Failure reason" -> r.body, "Status" -> r.status.toString),
            eventType = AuditConstants.eventTypeFailure)
          InvalidTaxEnrolmentsRequest(r.json)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex, auditMap, AuditConstants.transactionTaxEnrolmentsIssuer)
    }
  }

  private[connectors] def recoverRequest(putUrl: String, ex: Throwable, auditMap: Map[String, String],
                                         auditTransactionName: String)
                                        (implicit headerCarrier: HeaderCarrier): TaxEnrolmentsResponse = {
    ex match {
      case _: InternalServerException =>
        Logger.warn(s"Tax Enrolments reported an internal server error status to Url $putUrl")
        auditLogger.audit(transactionName = auditTransactionName,
          detail = auditMap,
          eventType = AuditConstants.eventTypeInternalServerError)
        TaxEnrolmentsErrorResponse

      case _: BadGatewayException =>
        Logger.warn(s"Tax Enrolments reported a bad gateway status to Url $putUrl")
        auditLogger.audit(transactionName = auditTransactionName,
          detail = auditMap,
          eventType = AuditConstants.eventTypeBadGateway)
        TaxEnrolmentsErrorResponse

      case _: Exception =>
        Logger.warn(s"Tax Enrolments reported a ${ex.printStackTrace()} to Url $putUrl")
        auditLogger.audit(transactionName = auditTransactionName,
          detail = auditMap,
          eventType = AuditConstants.eventTypeGeneric)
        TaxEnrolmentsErrorResponse
    }
  }
}
