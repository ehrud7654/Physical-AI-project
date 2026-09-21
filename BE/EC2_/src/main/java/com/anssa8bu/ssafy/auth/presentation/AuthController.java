package com.anssa8bu.ssafy.auth.presentation;

import com.anssa8bu.ssafy.auth.application.AuthService;
import com.anssa8bu.ssafy.auth.dto.AuthenticatedUserResponse;
import com.anssa8bu.ssafy.auth.dto.EmailAvailabilityRequest;
import com.anssa8bu.ssafy.auth.dto.EmailAvailabilityResponse;
import com.anssa8bu.ssafy.auth.dto.LoginRequest;
import com.anssa8bu.ssafy.auth.dto.LoginResponse;
import com.anssa8bu.ssafy.auth.dto.SignupRequest;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.of(authService.login(request)));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
    authService.logout(UUID.fromString(jwt.getClaimAsString("sid")));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(201).body(ApiResponse.of(authService.signup(request)));
  }

  @GetMapping("/email-availability")
  public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> emailAvailability(
      @Valid @ModelAttribute EmailAvailabilityRequest request) {
    return ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(ApiResponse.of(authService.emailAvailability(request.email())));
  }
}
