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

import com.crapi.model.ChangePhoneForm;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class MailBodyTest {

  @Test
  public void changePhoneMailBodyEscapesUserInput() {
    ChangePhoneForm form = new ChangePhoneForm();
    form.setOld_number("<b>1</b>");
    form.setNew_number("<script>x</script>");
    form.setOtp("<i>");
    String body = MailBody.changeMailBody(form);
    Assertions.assertFalse(body.contains("<script>"));
    Assertions.assertFalse(body.contains("<b>1</b>"));
    Assertions.assertTrue(body.contains("&lt;script&gt;x&lt;/script&gt;"));
    Assertions.assertTrue(body.contains("&lt;i&gt;"));
  }

  @Test
  public void changePhoneMailBodyKeepsPlainValues() {
    ChangePhoneForm form = new ChangePhoneForm();
    form.setOld_number("1234567890");
    form.setNew_number("0987654321");
    form.setOtp("1234");
    String body = MailBody.changeMailBody(form);
    Assertions.assertTrue(body.contains("1234567890"));
    Assertions.assertTrue(body.contains("0987654321"));
    Assertions.assertTrue(body.contains("<b>1234</b>"));
  }
}
