package com.anssa8bu.ssafy.jetson.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.jetson.application.JetsonHeartbeatService;
import com.anssa8bu.ssafy.jetson.dto.JetsonHeartbeatRequest;
import com.anssa8bu.ssafy.jetson.dto.JetsonHeartbeatResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/heartbeat")
public class JetsonHeartbeatController {
  private final JetsonHeartbeatService service;

  public JetsonHeartbeatController(JetsonHeartbeatService service) {
    this.service = service;
  }

  @PostMapping
  public ApiResponse<JetsonHeartbeatResponse> heartbeat(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID deviceId,
      @Valid @RequestBody JetsonHeartbeatRequest request) {
    if (!deviceId.toString().equals(jwt.getClaimAsString("deviceId"))) {
      throw new com.anssa8bu.ssafy.common.exception.BusinessConflictException(
          "DEVICE_ID_MISMATCH", "인증된 장치와 요청 장치가 일치하지 않습니다.");
    }
    return ApiResponse.of(service.heartbeat(jwt, request));
  }
}
