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

package auth

import checks.ResidentIndividualCheck
import javax.inject.{Inject, Singleton}
import play.api.mvc.Result
import services.AuthService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class AuthorisedActions @Inject()(authService: AuthService) {

  private def createAuthorisedAction(f: => Boolean => Future[Result], authCheck: Boolean): Future[Result] = {
    f(authCheck)
  }

  def authorisedResidentIndividualAction(action: Boolean => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    for {
      authority <- authService.getAuthority()
      authorised <- ResidentIndividualCheck.check(authority)
      result <- createAuthorisedAction(action, authorised)
    } yield result
  }

}
