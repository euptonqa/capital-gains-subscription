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

import uk.gov.hmrc.play.test.UnitSpec

class UserFactsModelSpec extends UnitSpec {

  "The UserFactsModel" when {

    "no optional values are provided" should {
      val model = UserFactsModel("forename", "surname", "line1", "line2", None, None, None, "DE")

      "return a valid json value" which {
        val json = UserFactsModel.asJson(model)

        "contains acknowledgementReference" in {
          (json \ "acknowledgementReference").as[String].isEmpty shouldBe false
        }

        "contains isAnAgent" in {
          (json \ "isAnAgent").as[Boolean] shouldBe false
        }

        "contains isAGroup" in {
          (json \ "isAGroup").as[Boolean] shouldBe false
        }

        "contains firstName" in {
          (json \ "individual" \ "firstName").as[String] shouldBe "forename"
        }

        "contains lastName" in {
          (json \ "individual" \ "lastName").as[String] shouldBe "surname"
        }

        "contains addressLine1" in {
          (json \ "address" \ "addressLine1").as[String] shouldBe "line1"
        }

        "contains addressLine2" in {
          (json \ "address" \ "addressLine2").as[String] shouldBe "line2"
        }

        "does not contain addressLine3" in {
          (json \ "address" \ "addressLine3").asOpt[String] shouldBe None
        }

        "does not contain addressLine4" in {
          (json \ "address" \ "addressLine4").asOpt[String] shouldBe None
        }

        "does not contain postcode" in {
          (json \ "address" \ "postalCode").asOpt[String] shouldBe None
        }

        "contains countryCode" in {
          (json \ "address" \ "countryCode").as[String] shouldBe "DE"
        }
      }
    }

    "all optional values are provided" should {
      val model = UserFactsModel("forename", "surname", "line1", "line2", Some("line3"), Some("line4"), Some("XX11 1XX"), "GB")

      "return a valid json value" which {
        val json = UserFactsModel.asJson(model)

        "contains addressLine3" in {
          (json \ "address" \ "addressLine3").asOpt[String] shouldBe Some("line3")
        }

        "contains addressLine4" in {
          (json \ "address" \ "addressLine4").asOpt[String] shouldBe Some("line4")
        }

        "contains postcode" in {
          (json \ "address" \ "postalCode").asOpt[String] shouldBe Some("XX11 1XX")
        }

        "contains a countryCode of GB" in {
          (json \ "address" \ "countryCode").as[String] shouldBe "GB"
        }
      }
    }

    "no postcode is provided for a GB address" should {
      val model = UserFactsModel("forename", "surname", "line1", "line2", Some("line3"), Some("line4"), None, "GB")

      "return an exception" in {
        val exception = intercept[Exception] {
          UserFactsModel.asJson(model)
        }

        exception.getMessage shouldBe "Attempted to submit UK address without a postcode."
      }
    }
  }
}
