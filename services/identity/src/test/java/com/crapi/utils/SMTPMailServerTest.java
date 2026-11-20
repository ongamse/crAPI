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

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class SMTPMailServerTest {

  @Test
  public void requireNoControlCharsAcceptsPlainValue() {
    Assertions.assertEquals(
        "user@example.com", SMTPMailServer.requireNoControlChars("user@example.com", "recipient"));
  }

  @Test
  public void requireNoControlCharsRejectsCrLf() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            SMTPMailServer.requireNoControlChars(
                "user@example.com\r\nBcc: evil@example.com", "recipient"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.requireNoControlChars("subject\nX-Header: 1", "subject"));
  }

  @Test
  public void requireNoControlCharsRejectsNull() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.requireNoControlChars(null, "subject"));
  }

  @Test
  public void stripUnsafeBodyCharsKeepsWhitespaceRemovesControls() {
    Assertions.assertEquals(
        "<p>a\n\tb\r</p>", SMTPMailServer.stripUnsafeBodyChars("<p>a\u0000\n\tb\r\u0007</p>"));
  }

  @Test
  public void parseRecipientsRebuildsValidAddresses() throws Exception {
    jakarta.mail.internet.InternetAddress[] out =
        SMTPMailServer.parseRecipients("user@example.com, other@example.org");
    Assertions.assertEquals(2, out.length);
    Assertions.assertEquals("user@example.com", out[0].getAddress());
    Assertions.assertEquals("other@example.org", out[1].getAddress());
  }

  @Test
  public void parseRecipientsRejectsInjectionAndInvalid() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.parseRecipients("a@example.com\r\nBcc: evil@example.com"));
    Assertions.assertThrows(Exception.class, () -> SMTPMailServer.parseRecipients("not-an-address"));
    Assertions.assertThrows(
        Exception.class, () -> SMTPMailServer.parseRecipients("\"a b\"@example.com"));
  }

  @Test
  public void buildSubjectAcceptsFixedSubjectsAndRejectsOthers() {
    Assertions.assertEquals(
        "crAPI: Change Email Token", SMTPMailServer.buildSubject("crAPI: Change Email Token"));
    Assertions.assertEquals(
        "Unlock your account", SMTPMailServer.buildSubject("Unlock your account"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.buildSubject("hi\r\nBcc: x@y.z"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.buildSubject("<script>"));
  }
}
