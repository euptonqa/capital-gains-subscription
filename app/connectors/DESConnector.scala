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
import config.WSHttp
import play.api.Logger
import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http._
import play.api.http.Status._

import scala.concurrent.{ExecutionContext, Future}

sealed trait DesResponse
case class SuccessDesResponse(response: JsObject) extends DesResponse
case object NotFoundDesResponse extends DesResponse
case object DesErrorResponse extends DesResponse
case class InvalidDesRequest(message: String) extends DesResponse

@Singleton
class DESConnector @Inject()() extends HttpErrorFunctions {

  val serviceUrl = "http://google.com"
  val environment = "???"
  val token = "DES"
  val baseUrl = "/capital-gains-subscription/"
  val obtainBpUrl = "/obtainBp"

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

  def subscribe(): Future[HttpResponse] = ???

  def register(): Future[HttpResponse] = ???

  def obtainBp(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesResponse] = {
    val requestUrl = s"$serviceUrl$baseUrl$nino$obtainBpUrl"
    val jsonNino = Json.toJson(nino)
    val response = cPOST(requestUrl, jsonNino)

    response map {
      r =>
        r.status match {
          case OK =>
            Logger.info("Successful DES request for BP number")
            SuccessDesResponse(r.json.as[JsObject])
          case ACCEPTED =>
            Logger.info("Accepted DES request for BP number")
            SuccessDesResponse(r.json.as[JsObject])
          case CONFLICT =>
            Logger.info("Conflicted DES request for BP number - BP Number already in existence")
            SuccessDesResponse(r.json.as[JsObject])
          case BAD_REQUEST =>
            val message = (r.json \ "reason").as[String]
            Logger.warn(s"Error with the request $message")
            InvalidDesRequest(message)
        }
    } recover {
      case ex: NotFoundException =>
        Logger.warn("Not found exception for DES request for BP number")
        NotFoundDesResponse
      case ex: InternalServerException =>
        Logger.warn("Internal server error for DES request for BP number")
        DesErrorResponse
      case ex: BadGatewayException =>
        Logger.warn("Bad gateway status for DES request for BP number")
        DesErrorResponse
      case ex: Exception =>
        Logger.warn(s"Exception of ${ex.toString} for DES request for BP number")
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
