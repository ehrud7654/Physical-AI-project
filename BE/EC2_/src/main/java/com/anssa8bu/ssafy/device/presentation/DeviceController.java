package com.anssa8bu.ssafy.device.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.device.application.DeviceService;
import com.anssa8bu.ssafy.device.dto.DeviceCreateRequest;
import com.anssa8bu.ssafy.device.dto.DeviceResponse;
import com.anssa8bu.ssafy.device.dto.DeviceUpdateRequest;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

  private final DeviceService deviceService;

  public DeviceController(DeviceService deviceService) {
    this.deviceService = deviceService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<DeviceResponse>> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceCreateRequest request) {
    DeviceResponse response = deviceService.create(ownerId(jwt), request);
    return ResponseEntity.created(URI.create("/api/v1/devices/" + response.id()))
        .body(ApiResponse.of(response));
  }

  @GetMapping
  public ApiResponse<List<DeviceResponse>> findAll(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) DeviceType deviceType) {
    return ApiResponse.of(deviceService.findAll(ownerId(jwt), deviceType));
  }

  @GetMapping("/{deviceId}")
  public ApiResponse<DeviceResponse> findOne(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID deviceId) {
    return ApiResponse.of(deviceService.findOne(ownerId(jwt), deviceId));
  }

  @PatchMapping("/{deviceId}")
  public ApiResponse<DeviceResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID deviceId,
      @Valid @RequestBody DeviceUpdateRequest request) {
    return ApiResponse.of(deviceService.update(ownerId(jwt), deviceId, request));
  }

  private UUID ownerId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
