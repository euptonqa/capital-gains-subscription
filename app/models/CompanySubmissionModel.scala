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

import play.api.libs.json.Json

case class CompanySubmissionModel(
                                   sap: Option[String],
                                   contactAddress: Option[CompanyAddressModel],
                                   registeredAddress: Option[CompanyAddressModel]
                                 ) {
  require(CompanySubmissionModel.validateSAP(sap), s"SAP:$sap is not valid.")
  val ackRef = "stubbedAckRef"
  val organisationName = "stubbedOrganisationName"
  //TODO: Update once ackRef, organisationName is established, don't merge PR until unstubbed
  implicit val format = s"{\"acknowledgementReference\": \"${ackRef}\"," +
    s"\"isAnAgent\": false," +
    s"\"isAGroup\": false," +
    "s\"organisation\": {" +
    s"\"organisationName\": \"${organisationName}\"" +
    s"}" +
    s"\"foreignAddress\": {" +
      s"\"addressLine1\": \"${registeredAddress.get.addressLine1.get}\"," +
      s"\"addressLine2\": \"${registeredAddress.get.addressLine2.get}\"," +
      s"\"addressLine3\": \"${registeredAddress.get.addressLine3.getOrElse("null")}\","
      s"\"addressLine4\": \"${registeredAddress.get.addressLine4.getOrElse("null")}\","
  "}"
}

object CompanySubmissionModel {
  implicit val formats = Json.format[CompanySubmissionModel]
  implicit val format = s"{\"acknowledgementReference\": \"${ackRef}\"," +
                        s"\"isAnAgent\": false," +
                        s"\"isAGroup\": false," +
                        "s\"organisation\": {" +
                          s"\"organisationName\": \"{}\""
                        "}"

  def validateSAP(sap: Option[String]): Boolean = {
    //TODO: Are we *certain* this is a valid constraint?
    sap match {
      case Some(data) => data.length.equals(15)
      case _ => true
    }
  }
}

