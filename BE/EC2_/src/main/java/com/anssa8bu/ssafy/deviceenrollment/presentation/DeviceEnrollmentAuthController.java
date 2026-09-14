package com.anssa8bu.ssafy.deviceenrollment.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.deviceenrollment.application.DeviceEnrollmentService;
import com.anssa8bu.ssafy.deviceenrollment.dto.DeviceEnrollmentCompleteRequest;
import com.anssa8bu.ssafy.deviceenrollment.dto.DeviceEnrollmentCompleteResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device-auth")
public class DeviceEnrollmentAuthController {

  private final DeviceEnrollmentService service;

  public DeviceEnrollmentAuthController(DeviceEnrollmentService service) {
    this.service = service;
  }

  @PostMapping("/enroll")
  public ApiResponse<DeviceEnrollmentCompleteResponse> complete(
      @Valid @RequestBody DeviceEnrollmentCompleteRequest request) {
    return ApiResponse.of(service.complete(request));
  }
}
