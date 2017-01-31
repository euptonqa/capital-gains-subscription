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

package common

object AuditConstants {
  val splunk = "SPLUNK AUDIT:\n"
  val transactionDESSubscribe = "CGT DES Subscribe"
  val transactionDESObtainBP = "CGT DES Obtain BP"
  val transactionDESObtainBPGhost = "CGT DES Obtain BP Ghost"
  val transactionTaxEnrolmentsIssuer = "CGT Tax Enrolments Issuer"
  val transactionTaxEnrolmentsSubscribe = "CGT Tax Enrolments Subscribe"
  val eventTypeFailure: String = "CGTFailure"
  val eventTypeSuccess: String = "CGTSuccess"
  val eventTypeConflict: String = "CGTConflict"
  val eventTypeBadGateway: String = "BadGateway"
  val eventTypeNotFound: String = "NotFound"
  val eventTypeInternalServerError: String = "InternalServerError"
  val eventTypeGeneric: String = "UnexpectedError"
}
