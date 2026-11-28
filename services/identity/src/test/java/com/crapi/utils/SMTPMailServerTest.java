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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SMTPMailServerTest {

  @Mock private MailConfiguration mailConfiguration;

  @Mock private MailHogConfiguration mailhogConfiguration;

  @InjectMocks private SMTPMailServer smtpMailServer;

  @Test
  void sendMailRejectsLineBreakInRecipient() {
    smtpMailServer.sendMail("victim@example.com\r\nBcc: attacker@example.com", "body", "subject");
    Mockito.verify(mailConfiguration, Mockito.never()).sendmail();
  }

  @Test
  void sendMailRejectsLineBreakInSubject() {
    smtpMailServer.sendMail("victim@example.com", "body", "subject\r\nBcc: attacker@example.com");
    Mockito.verify(mailConfiguration, Mockito.never()).sendmail();
  }
}
