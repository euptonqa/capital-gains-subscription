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

import org.apache.commons.lang3.RandomStringUtils
import play.api.libs.json._

case class UserFactsModel(firstName: String,
                          lastName: String,
                          addressLineOne: String,
                          addressLineTwo: Option[String],
                          townOrCity: String,
                          county: Option[String],
                          postCode: String,
                          country: String) {
  val json: JsValue = JsObject(Seq(
    "acknowledgementReference" -> JsString(getUniqueAckNo),
    "isAgent" -> JsBoolean(false),
    "isAGroup" -> JsBoolean(false),
    "individual" -> JsObject(Seq(
      "firstName" -> JsString(firstName),
      "lastName" -> JsString(lastName)
    )),
    "address" -> JsObject(Seq(
      "addressLine1" -> JsString(addressLineOne),
      "addressLine2" -> JsString(addressLineTwo.get),
      //has to be a .get... it's non-optional
      "addressLine3" -> JsNull,
      "addressLine4" -> JsNull,
      "countryCode" -> JsString("countryCodeStub")
    )
    )
  ))

  def getUniqueAckNo: String = {
    val length = 32
    val nanoTime = System.nanoTime()
    val restChars = length - nanoTime.toString.length
    val randomChars = RandomStringUtils.randomAlphanumeric(restChars)
    randomChars + nanoTime
}

object UserFactsModel {
  implicit val formats: OFormat[UserFactsModel] = Json.format[UserFactsModel]
}
