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
import com.crapi.model.ChangePhoneForm;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class MailBodyTest {

  @Test
  public void changeEmailBodyEscapesUserValues() {
    ChangeEmailForm form = new ChangeEmailForm();
    form.setOld_email("<script>alert(1)</script>@a.com");
    form.setNew_email("\"><img src=x onerror=alert(1)>@b.com");
    form.setToken("tok<b>");
    String body = MailBody.changeMailBody(form);
    Assertions.assertFalse(body.contains("<script>"));
    Assertions.assertFalse(body.contains("<img src=x"));
    Assertions.assertFalse(body.contains("tok<b>"));
    Assertions.assertTrue(body.contains("&lt;script&gt;alert(1)&lt;/script&gt;@a.com"));
  }

  @Test
  public void changePhoneBodyEscapesUserValues() {
    ChangePhoneForm form = new ChangePhoneForm();
    form.setOld_number("<script>1");
    form.setNew_number("<i>2</i>");
    form.setOtp("12<");
    String body = MailBody.changeMailBody(form);
    Assertions.assertFalse(body.contains("<script>"));
    Assertions.assertFalse(body.contains("<i>2</i>"));
    Assertions.assertTrue(body.contains("&lt;script&gt;1"));
    Assertions.assertTrue(body.contains("12&lt;"));
  }

  @Test
  public void ordinaryValuesAreUnchanged() {
    ChangePhoneForm form = new ChangePhoneForm();
    form.setOld_number("9876543210");
    form.setNew_number("9123456789");
    form.setOtp("1234");
    String body = MailBody.changeMailBody(form);
    Assertions.assertTrue(body.contains("<b>9876543210</b>"));
    Assertions.assertTrue(body.contains("<b>9123456789</b>"));
    Assertions.assertTrue(body.contains("<b>1234</b>"));
  }
}
