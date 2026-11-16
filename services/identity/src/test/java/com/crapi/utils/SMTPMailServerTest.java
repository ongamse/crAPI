/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.utils;

import com.crapi.model.ChangeEmailForm;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class SMTPMailServerTest {

  @Test
  public void rejectHeaderInjectionRejectsCrLf() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.rejectHeaderInjection("a@b.com\r\nBcc: evil@x.com", "recipient"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.rejectHeaderInjection("a\nb", "subject"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.rejectHeaderInjection(null, "subject"));
  }

  @Test
  public void rejectHeaderInjectionAcceptsPlainValues() {
    SMTPMailServer.rejectHeaderInjection("user@example.com", "recipient");
    SMTPMailServer.rejectHeaderInjection("crAPI: Change Email Token", "subject");
  }

  @Test
  public void changeMailBodyEscapesHtml() {
    ChangeEmailForm form = new ChangeEmailForm();
    form.setOld_email("<script>alert(1)</script>");
    form.setNew_email("a@b.com");
    form.setToken("tok");
    String body = MailBody.changeMailBody(form);
    Assertions.assertFalse(body.contains("<script>"));
    Assertions.assertTrue(body.contains("&lt;script&gt;"));
  }
}
