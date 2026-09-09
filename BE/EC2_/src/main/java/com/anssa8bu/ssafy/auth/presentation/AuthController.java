package com.anssa8bu.ssafy.auth.presentation;

import com.anssa8bu.ssafy.auth.application.AuthService;
import com.anssa8bu.ssafy.auth.dto.LoginRequest;
import com.anssa8bu.ssafy.auth.dto.LoginResponse;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
}
