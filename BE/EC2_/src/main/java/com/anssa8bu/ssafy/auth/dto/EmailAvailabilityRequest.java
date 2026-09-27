package com.anssa8bu.ssafy.auth.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record EmailAvailabilityRequest(
    @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 320)
        String email) {
  public EmailAvailabilityRequest {
    email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
