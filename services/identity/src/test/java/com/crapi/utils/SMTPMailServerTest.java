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

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class SMTPMailServerTest {

  @Test
  public void validateHeaderValueAcceptsPlainSubject() {
    Assertions.assertEquals(
        "crAPI: Change Phone Number OTP",
        SMTPMailServer.validateHeaderValue("crAPI: Change Phone Number OTP", "subject"));
  }

  @Test
  public void validateHeaderValueRejectsCrLf() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.validateHeaderValue("hi\r\nBcc: evil@example.com", "subject"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.validateHeaderValue("a\nb", "subject"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.validateHeaderValue(null, "subject"));
  }

  @Test
  public void validateRecipientsAcceptsValidAddress() throws AddressException {
    InternetAddress[] parsed = SMTPMailServer.validateRecipients("user@example.com");
    Assertions.assertEquals(1, parsed.length);
    Assertions.assertEquals("user@example.com", parsed[0].getAddress());
  }

  @Test
  public void validateRecipientsParsesMultipleAddresses() throws AddressException {
    InternetAddress[] parsed = SMTPMailServer.validateRecipients("a@example.com,b@example.com");
    Assertions.assertEquals(2, parsed.length);
    Assertions.assertEquals("b@example.com", parsed[1].getAddress());
  }

  @Test
  public void validateRecipientsRejectsInjectedHeader() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.validateRecipients("user@example.com\r\nBcc: evil@example.com"));
  }

  @Test
  public void validateRecipientsRejectsInvalidAddress() {
    Assertions.assertThrows(
        AddressException.class, () -> SMTPMailServer.validateRecipients("not-an-email"));
  }
}
