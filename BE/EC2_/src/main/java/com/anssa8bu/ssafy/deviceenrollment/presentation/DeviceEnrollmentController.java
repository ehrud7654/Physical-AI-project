package com.anssa8bu.ssafy.deviceenrollment.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.deviceenrollment.application.DeviceEnrollmentService;
import com.anssa8bu.ssafy.deviceenrollment.dto.DeviceEnrollmentStartResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceEnrollmentController {

  private final DeviceEnrollmentService service;

  public DeviceEnrollmentController(DeviceEnrollmentService service) {
    this.service = service;
  }

  @PostMapping("/{deviceId}/enrollment")
  public ResponseEntity<ApiResponse<DeviceEnrollmentStartResponse>> start(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID deviceId) {
    DeviceEnrollmentStartResponse response =
        service.start(UUID.fromString(jwt.getSubject()), deviceId);
    return ResponseEntity.created(
            URI.create("/api/v1/devices/" + deviceId + "/enrollment/" + response.enrollmentId()))
        .body(ApiResponse.of(response));
  }
}
