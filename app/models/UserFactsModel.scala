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

import common.DesUtils._
import play.api.libs.json._

case class UserFactsModel(firstName: String,
                          lastName: String,
                          addressLineOne: String,
                          addressLineTwo: Option[String],
                          townOrCity: String,
                          county: Option[String],
                          postCode: String,
                          country: String)

object UserFactsModel {
  implicit val json: UserFactsModel => JsValue = model => JsObject(Seq(
    "acknowledgementReference" -> JsString(getUniqueAckNo),
    "isAgent" -> JsBoolean(false),
    "isAGroup" -> JsBoolean(false),
    "individual" -> JsObject(Seq(
      "firstName" -> JsString(model.firstName),
      "lastName" -> JsString(model.lastName)
    )),
    "address" -> JsObject(Seq(
      "addressLine1" -> JsString(model.addressLineOne),
      "addressLine2" -> JsString(model.addressLineTwo.getOrElse("")),
      //has to be a .get... it's non-optional
      "addressLine3" -> JsNull,
      "addressLine4" -> JsNull,
      "countryCode" -> JsString("countryCodeStub")
    )
    )
  ))
}
