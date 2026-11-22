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

import com.crapi.config.MailConfiguration;
import com.crapi.config.MailHogConfiguration;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

public class SMTPMailServerTest {

  @Test
  public void sanitizeHeaderValueStripsCrLf() {
    Assertions.assertEquals(
        "SubjectBcc: evil@x.com", SMTPMailServer.sanitizeHeaderValue("Subject\r\nBcc: evil@x.com"));
    Assertions.assertEquals("", SMTPMailServer.sanitizeHeaderValue(null));
    Assertions.assertEquals("crAPI OTP", SMTPMailServer.sanitizeHeaderValue("crAPI OTP"));
  }

  @Test
  public void sendMailRejectsCrLfInRecipientAndSubject() {
    SMTPMailServer server = new SMTPMailServer();
    server.mailConfiguration = Mockito.mock(MailConfiguration.class);
    server.mailhogConfiguration = Mockito.mock(MailHogConfiguration.class);
    server.sendMail("a@b.com\r\nBcc: evil@x.com", "<b>hi</b>", "subject");
    server.sendMail("a@b.com", "<b>hi</b>", "subject\r\nBcc: evil@x.com");
    // Rejected before any session is used to build or send a message.
    Mockito.verify(server.mailConfiguration, Mockito.never()).sendmail();
  }
}
