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

import com.google.inject.{Inject, Singleton}
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
case class SuccessTaxEnrolmentsResponse(string: JsObject) extends TaxEnrolmentsResponse
case object TaxEnrolmentsErrorResponse extends TaxEnrolmentsResponse
case class InvalidTaxEnrolmentsRequest(message: String) extends TaxEnrolmentsResponse

@Singleton
class TaxEnrolmentsConnector @Inject()(appConfig: ApplicationConfig) extends HttpErrorFunctions with ServicesConfig {

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
    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.info(s"Successful Tax Enrolments issue to Url $putUrl")
          SuccessTaxEnrolmentsResponse(r.json.as[JsObject])
        case BAD_REQUEST =>
          val message = (r.json \ "reason").as[String]
          Logger.warn(s"Tax Enrolments reported an error with the request $message to Url $putUrl")
          InvalidTaxEnrolmentsRequest(message)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex)
    }
  }

  def getSubscriberResponse(subscriptionId: String, body: JsValue)(implicit headerCarrier: HeaderCarrier): Future[TaxEnrolmentsResponse] = {
    val putUrl = s"""$serviceUrl$serviceContext/subscriptions/$subscriptionId/${TaxEnrolmentsKeys.subscriber}"""
    val response = cPUT(putUrl, body)
    response map { r =>
      r.status match {
        case NO_CONTENT =>
          Logger.info(s"Successful Tax Enrolments subscription to Url $putUrl")
          SuccessTaxEnrolmentsResponse(r.json.as[JsObject])
        case BAD_REQUEST | UNAUTHORIZED =>
          val message = (r.json \ "reason").as[String]
          Logger.warn(s"Tax Enrolments reported an error with the request $message to Url $putUrl")
          InvalidTaxEnrolmentsRequest(message)
      }
    } recover {
      case ex => recoverRequest(putUrl, ex)
    }
  }

  private[connectors] def recoverRequest(putUrl: String, ex: Throwable): TaxEnrolmentsResponse = {
    ex match {
      case _: InternalServerException =>
        Logger.warn(s"Tax Enrolments reported an internal server error status to Url $putUrl")
        TaxEnrolmentsErrorResponse
      case _: BadGatewayException =>
        Logger.warn(s"Tax Enrolments reported a bad gateway status to Url $putUrl")
        TaxEnrolmentsErrorResponse
      case _: Exception =>
        Logger.warn(s"Tax Enrolments reported a ${ex.toString}")
        TaxEnrolmentsErrorResponse
    }
  }
}