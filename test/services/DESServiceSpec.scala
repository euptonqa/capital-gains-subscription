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

package services

import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class DESServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  implicit val hc = mock[HeaderCarrier]


  "Calling DESService .subscribeUser" should {

    "return a CGT ref with a valid request" in {

    }


    "return an exception with an appropriate error message in the event of an invalid request" in {

    }
  }

  "Calling DESService .subscribeGhostUser" should {
    "return a CGT ref with a valid request in" in {

    }

    "return an exception an appropriate error message in the event of an invalid request" in {

    }
  }
}
