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
  private final com.anssa8bu.ssafy.auth.security.SessionCookieService cookies;

  public AuthController(AuthService authService, com.anssa8bu.ssafy.auth.security.SessionCookieService cookies) {
    this.authService = authService;
    this.cookies = cookies;
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
    cookies.checkOrigin(http, false);
    // Replacing a browser login also revokes its previous session.
    var result = authService.login(request);
    authService.logout(cookies.read(http));
    return loginResponse(result);
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<LoginResponse>> refresh(jakarta.servlet.http.HttpServletRequest http) {
    cookies.checkOrigin(http, true);
    return loginResponse(authService.refresh(cookies.read(http)));
  }

  @PostMapping("/session/activity")
  public ResponseEntity<ApiResponse<ActivityResponse>> activity(@AuthenticationPrincipal Jwt jwt) {
    var session = authService.activity(UUID.fromString(jwt.getClaimAsString("sid")), UUID.fromString(jwt.getSubject()));
    return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
        .body(ApiResponse.of(new ActivityResponse(session.expiresAt(), session.absoluteExpiresAt())));
  }

  public record ActivityResponse(java.time.Instant sessionExpiresAt, java.time.Instant absoluteExpiresAt) {}

  private ResponseEntity<ApiResponse<LoginResponse>> loginResponse(AuthService.LoginResult result) {
    return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
        .header(org.springframework.http.HttpHeaders.SET_COOKIE,
            cookies.issue(result.refreshToken(), result.response().absoluteExpiresAt()))
        .body(ApiResponse.of(result.response()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt, jakarta.servlet.http.HttpServletRequest http) {
    cookies.checkOrigin(http, true);
    authService.logout(cookies.read(http));
    if (jwt != null) {
      if (!"USER".equals(jwt.getClaimAsString("actorType")))
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
      authService.logout(UUID.fromString(jwt.getClaimAsString("sid")));
    }
    return ResponseEntity.noContent().header(org.springframework.http.HttpHeaders.SET_COOKIE, cookies.clear())
        .cacheControl(org.springframework.http.CacheControl.noStore()).build();
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
