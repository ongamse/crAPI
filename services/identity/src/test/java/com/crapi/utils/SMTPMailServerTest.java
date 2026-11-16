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

import org.junit.Assert;
import org.junit.Test;

public class SMTPMailServerTest {

  @Test
  public void containsControlCharsRejectsCrLfAndNull() {
    Assert.assertTrue(SMTPMailServer.containsControlChars(null));
    Assert.assertTrue(SMTPMailServer.containsControlChars("a@b.com\r\nBcc: x@y.com"));
    Assert.assertTrue(SMTPMailServer.containsControlChars("a@b.com\nBcc: x@y.com"));
    Assert.assertTrue(SMTPMailServer.containsControlChars("subject\r\nX-Header: 1"));
    Assert.assertTrue(SMTPMailServer.containsControlChars("a\tb"));
  }

  @Test
  public void containsControlCharsAllowsNormalValues() {
    Assert.assertFalse(SMTPMailServer.containsControlChars("user@example.com"));
    Assert.assertFalse(SMTPMailServer.containsControlChars("crAPI: Change Email Token"));
  }
}
