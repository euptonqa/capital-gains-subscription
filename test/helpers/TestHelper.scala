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

package helpers

import models.SubscriptionReferenceModel

object TestHelper extends TestHelper

trait TestHelper {

  val dummySubscriptionRequestValid = SubscriptionReferenceModel("CGTUNIQUEREF")
  val dummySubscriptionRequestBadRequest = SubscriptionReferenceModel("badrequest")
  val dummySubscriptionRequestNotFound = SubscriptionReferenceModel("notfound")
  val dummySubscriptionRequestDuplicate= SubscriptionReferenceModel("duplicate")
  val dummySubscriptionRequestServerError = SubscriptionReferenceModel("servererror")
  val dummySubscriptionRequestServiceUnavailable= SubscriptionReferenceModel("serviceunavailable")
  val dummySubscriptionRequestMissingRegime = SubscriptionReferenceModel("missingregime")
  val dummySubscriptionRequestSapNumberMissing = SubscriptionReferenceModel("sapnumbermissing")
  val dummySubscriptionRequestNotProcessed = SubscriptionReferenceModel("notprocessed")
  val dummyValidSafeID = "XA0001234567890"
  val dummyInvalidSafeID = "YA0001234567890"

}
