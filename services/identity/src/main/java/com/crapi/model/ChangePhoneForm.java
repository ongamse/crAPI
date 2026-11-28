package com.crapi.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePhoneForm {
  // Digits and common phone punctuation only; no CR/LF or markup characters.
  public static final String PHONE_PATTERN = "[0-9+\\-() ]{1,15}";

  @NotBlank
  @Size(max = 15)
  @Pattern(regexp = PHONE_PATTERN)
  private String old_number;

  @NotBlank
  @Size(max = 15)
  @Pattern(regexp = PHONE_PATTERN)
  private String new_number;

  @Size(min = 3, max = 4)
  private String otp;
}
