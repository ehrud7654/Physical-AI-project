package com.anssa8bu.ssafy.deployment.presentation;

import static com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.CreateRequest;
import static com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.DeploymentResponse;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.deployment.application.DeploymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/robots/{robotId}/deployments")
public class DeploymentController {
  private final DeploymentService service;

  public DeploymentController(DeploymentService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<DeploymentResponse>> create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID robotId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateRequest request) {
    DeploymentResponse result =
        service.create(owner(jwt), robotId, request.taskVersionId(), idempotencyKey);
    return ResponseEntity.created(
            URI.create("/api/v1/robots/" + robotId + "/deployments/" + result.id()))
        .body(ApiResponse.of(result));
  }

  @GetMapping
  public ApiResponse<List<DeploymentResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID robotId) {
    return ApiResponse.of(service.list(owner(jwt), robotId));
  }

  @GetMapping("/{deploymentId}")
  public ApiResponse<DeploymentResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID robotId,
      @PathVariable UUID deploymentId) {
    return ApiResponse.of(service.get(owner(jwt), robotId, deploymentId));
  }

  @PostMapping("/{deploymentId}/cancel")
  public ApiResponse<DeploymentResponse> cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID robotId,
      @PathVariable UUID deploymentId) {
    return ApiResponse.of(service.cancel(owner(jwt), robotId, deploymentId));
  }

  private UUID owner(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
