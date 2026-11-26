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

  private static final char LS = (char) 0x2028;
  private static final char PS = (char) 0x2029;

  @Test
  public void recipientWithControlCharactersIsDetected() {
    Assertions.assertTrue(SMTPMailServer.containsControlChars("a@b.com\r\nBcc: evil@x.com"));
    Assertions.assertTrue(SMTPMailServer.containsControlChars("a@b.com\nBcc: evil@x.com"));
    Assertions.assertTrue(SMTPMailServer.containsControlChars("a@b.com" + LS));
    Assertions.assertTrue(SMTPMailServer.containsControlChars("a@b.com" + PS + "x"));
    Assertions.assertFalse(SMTPMailServer.containsControlChars("user@example.com"));
  }

  @Test
  public void subjectControlCharactersAreStripped() {
    Assertions.assertEquals(
        "HelloBcc: evil@x.com", SMTPMailServer.stripControlChars("Hello\r\nBcc: evil@x.com"));
    Assertions.assertEquals("abc", SMTPMailServer.stripControlChars("a" + LS + "b" + PS + "c"));
    Assertions.assertEquals("crAPI OTP", SMTPMailServer.stripControlChars("crAPI OTP"));
    Assertions.assertNull(SMTPMailServer.stripControlChars(null));
  }
}
