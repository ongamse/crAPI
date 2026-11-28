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

package com.crapi.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeEmailForm {

  // Strict address charset: rejects CR/LF, whitespace, quotes, angle brackets and commas so the
  // values can never carry extra headers or recipients into a mail.
  public static final String EMAIL_PATTERN = "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+";

  @NotBlank
  @Size(min = 3, max = 40)
  @Email
  @Pattern(regexp = EMAIL_PATTERN)
  private String old_email;

  @NotBlank
  @Size(min = 3, max = 40)
  @Email
  @Pattern(regexp = EMAIL_PATTERN)
  private String new_email;

  private String token;
}
