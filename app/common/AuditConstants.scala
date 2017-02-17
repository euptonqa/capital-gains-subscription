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

trait AuditConstants {
  val splunk = "SPLUNK AUDIT:\n"
  val transactionDESSubscribe = "CGT DES Subscribe"
  val transactionDESObtainSAP = "CGT DES Obtain SAP"
  val transactionDESObtainSAPGhost = "CGT DES Obtain SAP Ghost"
  val transactionDESGetExistingSAP = "CGT DES Get Subscription"
  val transactionTaxEnrolmentsIssuer = "CGT Tax Enrolments Issuer"
  val transactionTaxEnrolmentsSubscribe = "CGT Tax Enrolments Subscribe"
  val transactionTaxEnrolmentsIssuerAgent = "CGT Agent Tax Enrolments Issuer"
  val transactionTaxEnrolmentsSubscribeAgent = "CGT Agent Tax EnrolmentsSubscribe"
  val eventTypeFailure: String = "CGTFailure"
  val eventTypeSuccess: String = "CGTSuccess"
  val eventTypeConflict: String = "CGTConflict"
  val eventTypeBadGateway: String = "BadGateway"
  val eventTypeNotFound: String = "NotFound"
  val eventTypeInternalServerError: String = "InternalServerError"
  val eventTypeGeneric: String = "UnexpectedError"
}

object AuditConstants extends AuditConstants {

}

object AuditConstantsAgent extends AuditConstants {
  override val transactionDESSubscribe = "Agent CGT DES Subscribe"
  override val transactionDESObtainSAP = "Agent CGT DES Obtain SAP"
  override val transactionDESObtainSAPGhost = "Agent CGT DES Obtain SAP Ghost"
  override val transactionDESGetExistingSAP = "Agent CGT DES Get Subscription"
  override val transactionTaxEnrolmentsIssuer = "Agent CGT Tax Enrolments Issuer"
  override val transactionTaxEnrolmentsSubscribe = "Agent CGT Tax Enrolments Subscribe"
  override val transactionTaxEnrolmentsIssuerAgent = "Agent CGT Agent Tax Enrolments Issuer"
  override val transactionTaxEnrolmentsSubscribeAgent = "Agent CGT Agent Tax EnrolmentsSubscribe"
  override val eventTypeFailure: String = "AgentCGTFailure"
  override val eventTypeSuccess: String = "AgentCGTSuccess"
  override val eventTypeConflict: String = "AgentCGTConflict"
}