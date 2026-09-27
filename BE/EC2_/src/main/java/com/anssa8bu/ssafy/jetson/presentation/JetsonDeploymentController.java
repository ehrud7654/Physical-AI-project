package com.anssa8bu.ssafy.jetson.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.DeploymentResponse;
import com.anssa8bu.ssafy.jetson.application.JetsonDeploymentService;
import com.anssa8bu.ssafy.jetson.dto.JetsonDeploymentModels.Manifest;
import com.anssa8bu.ssafy.jetson.dto.JetsonDeploymentModels.ReportRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/robots/me/deployments")
public class JetsonDeploymentController {
  private final JetsonDeploymentService service;

  public JetsonDeploymentController(JetsonDeploymentService service) {
    this.service = service;
  }

  @GetMapping("/pending")
  public ResponseEntity<ApiResponse<Manifest>> pending(@AuthenticationPrincipal Jwt jwt) {
    return service
        .pending(jwt)
        .map(value -> ResponseEntity.ok(ApiResponse.of(value)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/{deploymentId}/manifest")
  public ApiResponse<Manifest> renewManifest(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID deploymentId) {
    return ApiResponse.of(service.manifest(jwt, deploymentId));
  }

  @PatchMapping("/{deploymentId}")
  public ApiResponse<DeploymentResponse> report(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID deploymentId,
      @Valid @RequestBody ReportRequest request) {
    return ApiResponse.of(service.report(jwt, deploymentId, request));
  }
}
