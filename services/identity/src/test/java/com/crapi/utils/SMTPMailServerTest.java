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
  public void containsControlCharsRejectsCrlf() {
    Assertions.assertTrue(SMTPMailServer.containsControlChars("a@b.com\r\nBcc: evil@x.com", false));
    Assertions.assertTrue(SMTPMailServer.containsControlChars("subject\nBcc: x", false));
  }

  @Test
  public void containsControlCharsAcceptsPlainValues() {
    Assertions.assertFalse(SMTPMailServer.containsControlChars("user@example.com", false));
    Assertions.assertFalse(SMTPMailServer.containsControlChars(null, false));
    Assertions.assertFalse(SMTPMailServer.containsControlChars("line1\r\nline2", true));
  }

  @Test
  public void changeMailBodyEscapesHtml() {
    ChangeEmailForm form = new ChangeEmailForm();
    form.setOld_email("old@example.com");
    form.setNew_email("<script>alert(1)</script>@example.com");
    form.setToken("tok");
    String body = MailBody.changeMailBody(form);
    Assertions.assertFalse(body.contains("<script>"));
  }
}
