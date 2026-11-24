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

import jakarta.mail.internet.InternetAddress;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class SMTPMailServerTest {

  @Test
  public void validRecipientsAreRebuilt() throws Exception {
    InternetAddress[] result =
        SMTPMailServer.buildValidatedRecipients("a.b+c@example.com, d@mail.example.org");
    Assertions.assertEquals(2, result.length);
    Assertions.assertEquals("a.b+c@example.com", result[0].getAddress());
    Assertions.assertEquals("d@mail.example.org", result[1].getAddress());
  }

  @Test
  public void recipientWithCrlfIsRejected() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.buildValidatedRecipients("a@example.com\r\nBcc: evil@example.com"));
  }

  @Test
  public void recipientWithDisplayNameIsRejected() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.buildValidatedRecipients("Evil <evil@example.com>"));
  }

  @Test
  public void anyInvalidEntryRejectsWholeList() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SMTPMailServer.buildValidatedRecipients("a@example.com,not-an-email"));
  }

  @Test
  public void emptyOrNullRecipientIsRejected() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.buildValidatedRecipients(null));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.buildValidatedRecipients(""));
  }

  @Test
  public void bodyKeepsAppMarkupAndDropsControlChars() {
    Assertions.assertEquals(
        "<html><body><b>Hi</b>&nbsp;x\r\n</body></html>",
        SMTPMailServer.rebuildHtmlBody("<html><body><b>Hi</b>&nbsp;x\r\n</body></html>"));
    Assertions.assertEquals("ab", SMTPMailServer.rebuildHtmlBody("a\u0000\u0007b"));
  }

  @Test
  public void bodyNonAsciiBecomesNumericReference() {
    Assertions.assertEquals("&#233;", SMTPMailServer.rebuildHtmlBody("\u00e9"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.rebuildHtmlBody(null));
  }

  @Test
  public void subjectControlCharsAreStrippedAndLengthCapped() {
    Assertions.assertEquals(
        "HelloBcc: x@example.com", SMTPMailServer.sanitizeSubject("Hello\r\nBcc: x@example.com"));
    Assertions.assertEquals(255, SMTPMailServer.sanitizeSubject("a".repeat(1000)).length());
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> SMTPMailServer.sanitizeSubject(null));
  }
}
