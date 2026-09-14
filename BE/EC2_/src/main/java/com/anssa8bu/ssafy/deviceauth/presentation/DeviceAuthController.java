package com.anssa8bu.ssafy.deviceauth.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.deviceauth.application.DeviceAuthenticationService;
import com.anssa8bu.ssafy.deviceauth.dto.DeviceTokenRequest;
import com.anssa8bu.ssafy.deviceauth.dto.DeviceTokenResponse;
import com.anssa8bu.ssafy.skill.dto.SkillResponses.SkillResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device-auth")
public class DeviceAuthController {

  private final DeviceAuthenticationService authenticationService;

  public DeviceAuthController(DeviceAuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping("/token")
  public ResponseEntity<ApiResponse<DeviceTokenResponse>> issueToken(
      @Valid @RequestBody DeviceTokenRequest request) {
    return ResponseEntity.ok(ApiResponse.of(authenticationService.issueToken(request)));
  }

  @PostMapping("/skills")
  public ApiResponse<List<SkillResponse>> availableSkills(
      @Valid @RequestBody DeviceTokenRequest request) {
    return ApiResponse.of(authenticationService.listAvailableSkills(request));
  }
}
