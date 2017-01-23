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

import models.SubscriptionRequest

object TestHelper extends TestHelper

trait TestHelper {

  val dummySubscriptionRequestValid = SubscriptionRequest("CGTUNIQUEREF")
  val dummySubscriptionRequestBadRequest = SubscriptionRequest("badrequest")
  val dummySubscriptionRequestNotFound = SubscriptionRequest("notfound")
  val dummySubscriptionRequestDuplicate= SubscriptionRequest("duplicate")
  val dummySubscriptionRequestServerError = SubscriptionRequest("servererror")
  val dummySubscriptionRequestServiceUnavailable= SubscriptionRequest("serviceunavailable")
  val dummySubscriptionRequestMissingRegime = SubscriptionRequest("missingregime")
  val dummySubscriptionRequestSapNumberMissing = SubscriptionRequest("sapnumbermissing")
  val dummySubscriptionRequestNotProcessed = SubscriptionRequest("notprocessed")
  val dummyValidSafeID = "XA0001234567890"
  val dummyInvalidSafeID = "YA0001234567890"

}
