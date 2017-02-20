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

import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class RoutesSpec extends UnitSpec with WithFakeApplication {

  "The URL for the subscribeKnownIndividual Action" should {
    "be equal to /capital-gains-subscription/subscribe/resident/individual" in {
      val path = controllers.routes.SubscriptionController.subscribeKnownIndividual("AA123456A")
      path.url shouldBe "/capital-gains-subscription/subscribe/resident/individual?nino=AA123456A"
    }
  }


  "The URL for the subscribeCompany Action" should {
    "be equal to /capital-gains-subscription/subscribe/company" in {
      val path = controllers.routes.SubscriptionController.subscribeCompany()
      path.url shouldBe "/capital-gains-subscription/subscribe/company"
    }
  }

  "The URL for the subscribeKnownIndividual Action" should {
    "be equal to /capital-gains-subscription/subscribe/non-resident/individual-nino" in {
      val path = controllers.routes.SubscriptionController.subscribeNonResidentNinoIndividual("AA123456A")
      path.url shouldBe "/capital-gains-subscription/subscribe/non-resident/individual-nino?nino=AA123456A"
    }
  }

  "The URL for the subscribeGhostIndividual Action" should {
    "be equal to /capital-gains-subscription/subscribe/non-resident/individual" in {
      val path = controllers.routes.SubscriptionController.subscribeGhostIndividual()
      path.url shouldBe "/capital-gains-subscription/subscribe/non-resident/individual"
    }
  }

  "The URL for the subscribeAgent Action" should {
    "be equal to /capital-gains-subscription/subscribe/agent" in {
      val path = controllers.routes.SubscriptionController.subscribeAgent()
      path.url shouldBe "/capital-gains-subscription/subscribe/agent"
    }
  }
}
