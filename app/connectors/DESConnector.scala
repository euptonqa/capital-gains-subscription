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
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
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
  val token = "blah"
  val baseUrl = "/capital-gains-subscription/"
  val obtainBpUrl = "/obtainBp"

  val urlHeaderEnvironment = "??? see srcs, found in config"
  val urlHeaderAuthorization = "??? same as above"

  val http: HttpGet with HttpPost with HttpPut = WSHttp


  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse) = {
    response.status match {
      case 400 => response
      case 404 => throw new NotFoundException("ETMP returned a Not Found status")
      case 409 => response
      case 500 => throw new InternalServerException("ETMP returned an internal server error")
      case 502 => throw new BadGatewayException("ETMP returned an upstream error")
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
    val desHeaders = hc.copy(authorization = Some(Authorization(s"Bearer $token"))).withExtraHeaders("Environment" -> environment)
    val jsonNino = Json.toJson(nino)
    val response = cPOST(requestUrl, jsonNino)

    response map {
      r =>
        r.status match {
          case OK =>
            SuccessDesResponse(r.json.as[JsObject])
          case ACCEPTED =>
            SuccessDesResponse(r.json.as[JsObject])
          case CONFLICT =>
            SuccessDesResponse(r.json.as[JsObject])
          case BAD_REQUEST =>
            val message = (r.json \ "reason").as[String]
            InvalidDesRequest(message)
        }
    } recover {
      case ex: NotFoundException =>
        NotFoundDesResponse
      case ex: InternalServerException =>
        DesErrorResponse
      case ex: BadGatewayException =>
        DesErrorResponse
      case ex: Exception =>
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
