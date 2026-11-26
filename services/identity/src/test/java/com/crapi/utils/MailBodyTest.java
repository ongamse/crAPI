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
  public void changePhoneBodyEscapesAttackerControlledValues() {
    ChangePhoneForm form = new ChangePhoneForm();
    form.setOld_number("<script>alert(1)</script>");
    form.setNew_number("<a href='http://evil'>x</a>");
    form.setOtp("12\"><b>");

    String body = MailBody.changeMailBody(form);

    Assertions.assertFalse(body.contains("<script>alert(1)</script>"));
    Assertions.assertFalse(body.contains("<a href='http://evil'>"));
    Assertions.assertTrue(body.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
  }

  @Test
  public void changeEmailBodyEscapesAttackerControlledValues() {
    ChangeEmailForm form = new ChangeEmailForm();
    form.setOld_email("<img src=x onerror=alert(1)>@a.com");
    form.setNew_email("<b>new</b>@a.com");
    form.setToken("tok");

    String body = MailBody.changeMailBody(form);

    Assertions.assertFalse(body.contains("<img src=x"));
    Assertions.assertFalse(body.contains("<b>new</b>@a.com"));
    Assertions.assertTrue(body.contains("&lt;b&gt;new&lt;/b&gt;@a.com"));
  }

  @Test
  public void changeBodyKeepsPlainValues() {
    ChangePhoneForm form = new ChangePhoneForm();
    form.setOld_number("1234567890");
    form.setNew_number("0987654321");
    form.setOtp("1234");

    String body = MailBody.changeMailBody(form);

    Assertions.assertTrue(body.contains("<b>1234567890</b>"));
    Assertions.assertTrue(body.contains("<b>0987654321</b>"));
    Assertions.assertTrue(body.contains("<b>1234</b>"));
  }
}
