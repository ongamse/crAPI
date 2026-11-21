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

package com.crapi.exception;

import com.crapi.model.ErrorDetails;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

public class ExceptionHandlerTest {

  static class Form {
    private String email;

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }
  }

  @SuppressWarnings("unused")
  void target(Form form) {}

  @Test
  public void validationErrorResponseContainsFieldAndMessageOnly() throws Exception {
    String rejected = "secret-rejected-value";
    BindingResult bindingResult = new BeanPropertyBindingResult(new Form(), "form");
    bindingResult.addError(
        new FieldError("form", "email", rejected, false, null, null, "must be a valid email"));
    MethodParameter parameter =
        new MethodParameter(ExceptionHandlerTest.class.getDeclaredMethod("target", Form.class), 0);
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(parameter, bindingResult);

    ResponseEntity<Object> response =
        new ExceptionHandler()
            .handleMethodArgumentNotValid(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, null);

    Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ErrorDetails body = (ErrorDetails) response.getBody();
    Assertions.assertEquals("Validation failed", body.getMessage());
    Assertions.assertTrue(body.getDetails().contains("email"));
    Assertions.assertTrue(body.getDetails().contains("must be a valid email"));
    Assertions.assertFalse(body.getDetails().contains(rejected));
    Assertions.assertFalse(body.getDetails().contains("BeanPropertyBindingResult"));
  }
}
