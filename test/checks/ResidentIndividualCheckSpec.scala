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

class ResidentIndividualCheckSpec extends UnitSpec {

  "Calling the ResidentIndividualCheck" should {

    "return a true with valid authority" in {
      val authorisationDataModel = AuthorisationDataModel(AffinityConstants.individual, ConfidenceLevel.L200, CredentialStrengthConstants.strong)
      val result = ResidentIndividualCheck.check(Some(authorisationDataModel))

      result shouldBe true
    }

    "return a false with a confidence level below 200" in {
      val authorisationDataModel = AuthorisationDataModel(AffinityConstants.individual, ConfidenceLevel.L50, CredentialStrengthConstants.strong)
      val result = ResidentIndividualCheck.check(Some(authorisationDataModel))

      result shouldBe false
    }

    "return a false with an non-individual" in {
      val authorisationDataModel = AuthorisationDataModel(AffinityConstants.organisation, ConfidenceLevel.L200, CredentialStrengthConstants.strong)
      val result = ResidentIndividualCheck.check(Some(authorisationDataModel))

      result shouldBe false
    }

    "return a false with weak credentials" in {
      val authorisationDataModel = AuthorisationDataModel(AffinityConstants.individual, ConfidenceLevel.L200, CredentialStrengthConstants.weak)
      val result = ResidentIndividualCheck.check(Some(authorisationDataModel))

      result shouldBe false
    }

    "return a false with no authority" in {
      val result = ResidentIndividualCheck.check(None)

      result shouldBe false
    }
  }
}
