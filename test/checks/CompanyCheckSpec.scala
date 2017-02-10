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

package checks

import common.{AffinityConstants, CredentialStrengthConstants}
import models.AuthorisationDataModel
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.test.UnitSpec

class CompanyCheckSpec extends UnitSpec {

  "Calling the OrganisationCheck" should {

    "return true with a valid authority" in {
      val authorisationDataModel = AuthorisationDataModel(AffinityConstants.organisation, ConfidenceLevel.L50, CredentialStrengthConstants.strong)
      val result = OrganisationCheck.check(Some(authorisationDataModel))

      await(result) shouldBe true
    }

    "return false with a non-organisation" in {
      val authorisationDataModel = AuthorisationDataModel(AffinityConstants.individual, ConfidenceLevel.L50, CredentialStrengthConstants.strong)
      val result = OrganisationCheck.check(Some(authorisationDataModel))

      await(result) shouldBe false
    }
  }
}
