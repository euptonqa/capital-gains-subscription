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

package models

import play.api.Logger
import play.api.libs.json.{JsValue, Json, OFormat}

case class CompanySubmissionModel(
                                   sap: Option[String],
                                   contactAddress: Option[CompanyAddressModel],
                                   registeredAddress: Option[CompanyAddressModel]
                                 ) {
  require(CompanySubmissionModel.validateSAP(sap), s"SAP:$sap is not valid.")

  def toSubscriptionPayload: JsValue = {
    val filteredParameters = Seq(
      "line1" -> registeredAddress.get.addressLine1.get,
      "line2" -> registeredAddress.get.addressLine2.get,
      "line3" -> registeredAddress.get.addressLine3,
      "line4" -> registeredAddress.get.addressLine4,
      "postalCode" -> {
        if (registeredAddress.get.country.get == "GB") {
          Logger.warn("Attempted to submit UK address without a postcode.")
          throw new Exception("Attempted to submit UK address without a postcode.")
        }
        else registeredAddress.get.postCode
      },
      "countryCode" -> registeredAddress.get.country.get
    ).filterNot(entry => entry._2 == None).map {
      case (key, result: String) => (key, Json.toJsFieldJsValueWrapper(result))
      case (key, result: Option[String]) => (key, Json.toJsFieldJsValueWrapper(result))
    }

    Json.obj(
      "addressDetail" -> Json.obj(
        filteredParameters: _*
      )
    )
  }
}

object CompanySubmissionModel {
  implicit val formats: OFormat[CompanySubmissionModel] = Json.format[CompanySubmissionModel]

  def validateSAP(sap: Option[String]): Boolean = {
    sap match {
      case Some(data) => data.length == 15
      case _ => true
    }
  }
}

