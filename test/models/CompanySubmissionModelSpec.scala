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

class CompanySubmissionModelSpec extends UnitSpec {

  "Creating a company submission model" which {

    "has an invalid sap" should {
      val sap = Some("123456789")
      lazy val ex = intercept[Exception] {
        CompanySubmissionModel(sap, None, None)
      }

      "throw an exception" in {
        ex.getMessage shouldBe s"requirement failed: SAP:$sap is not valid."
      }
    }
  }

  "Calling .toSubscriptionPayload" should {

    "return a valid json payload with no optional values" which {
      val model = CompanySubmissionModel(None, None, Some(CompanyAddressModel(Some("line1"), Some("line2"), None, None, None, Some("DE"))))
      val json = model.toSubscriptionPayload

      "contains line1" in {
        (json \ "addressDetail" \ "line1").as[String] shouldBe "line1"
      }

      "contains line2" in {
        (json \ "addressDetail" \ "line2").as[String] shouldBe "line2"
      }

      "does not contain line3" in {
        (json \ "addressDetail" \ "line3").asOpt[String] shouldBe None
      }

      "does not contain line4" in {
        (json \ "addressDetail" \ "line4").asOpt[String] shouldBe None
      }

      "does not contain postalCode" in {
        (json \ "addressDetail" \ "postalCode").asOpt[String] shouldBe None
      }

      "contains countryCode" in {
        (json \ "addressDetail" \ "countryCode").as[String] shouldBe "DE"
      }
    }

    "return a valid json payload with all optional values" which {
      val model = CompanySubmissionModel(None, None, Some(CompanyAddressModel(Some("line1"), Some("line2"), Some("line3"),
        Some("line4"), Some("postcode"), Some("DE"))))
      val json = model.toSubscriptionPayload

      "contains line1" in {
        (json \ "addressDetail" \ "line1").as[String] shouldBe "line1"
      }

      "contains line2" in {
        (json \ "addressDetail" \ "line2").as[String] shouldBe "line2"
      }

      "contains line3" in {
        (json \ "addressDetail" \ "line3").asOpt[String] shouldBe Some("line3")
      }

      "contains line4" in {
        (json \ "addressDetail" \ "line4").asOpt[String] shouldBe Some("line4")
      }

      "contains postalCode" in {
        (json \ "addressDetail" \ "postalCode").asOpt[String] shouldBe Some("postcode")
      }

      "contains countryCode" in {
        (json \ "addressDetail" \ "countryCode").as[String] shouldBe "DE"
      }
    }

    "throw an exception when no postcode is provided for a UK address" in {
      val model = CompanySubmissionModel(None, None, Some(CompanyAddressModel(Some("line1"), Some("line2"), None, None, None, Some("GB"))))
      val exception = intercept[Exception]{
        model.toSubscriptionPayload
      }

      exception.getMessage shouldBe "Attempted to submit UK address without a postcode."
    }
  }
}
