package com.anssa8bu.ssafy.auth.dto;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record SignupRequest(
    @NotBlank(message = "이름은 필수입니다.") @Size(max = 100, message = "이름은 100자 이하여야 합니다.") String name,
    @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 320)
        String email,
    @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상, 72자 이하여야 합니다.")
        String password) {
  public SignupRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
  public boolean isPasswordWithinByteLimit() {
    return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }
}
