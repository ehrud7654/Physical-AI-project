package com.anssa8bu.ssafy.robot.presentation;

import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.deployment.repository.DeploymentRepository;
import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import com.anssa8bu.ssafy.execution.repository.RobotExecutionRepository;
import com.anssa8bu.ssafy.robot.application.RobotRegistrationService;
import com.anssa8bu.ssafy.robot.dto.RobotCreateRequest;
import com.anssa8bu.ssafy.robot.dto.RobotResponse;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/robots")
public class RobotController {
  private static final Set<RobotExecutionStatus> ACTIVE =
      EnumSet.of(
          RobotExecutionStatus.REQUESTED,
          RobotExecutionStatus.STARTING,
          RobotExecutionStatus.RUNNING,
          RobotExecutionStatus.CANCEL_REQUESTED,
          RobotExecutionStatus.STATUS_UNKNOWN);
  private final RuntimeStateService runtime;
  private final RobotRepository robots;
  private final RobotExecutionRepository executions;
  private final DeploymentRepository deployments;
  private final RobotRegistrationService registrationService;

  public RobotController(
      RobotRepository r,
      RobotExecutionRepository e,
      DeploymentRepository d,
      RobotRegistrationService registrationService,
      RuntimeStateService runtime) {
    robots = r;
    executions = e;
    deployments = d;
    this.registrationService = registrationService;
    this.runtime = runtime;
  }

  @PostMapping
  @Transactional
  public ResponseEntity<ApiResponse<RobotResponse>> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RobotCreateRequest request) {
    Robot saved = registrationService.create(owner(jwt), request);
    RobotResponse response = map(saved);
    return ResponseEntity.created(URI.create("/api/v1/robots/" + response.id()))
        .body(ApiResponse.of(response));
  }

  @GetMapping
  @Transactional(readOnly = true)
  public ApiResponse<List<RobotResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.of(
        robots.findAllByOwnerIdOrderByCreatedAtDesc(owner(jwt)).stream().map(this::map).toList());
  }

  @GetMapping("/{robotId}")
  @Transactional(readOnly = true)
  public ApiResponse<RobotResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID robotId) {
    return ApiResponse.of(
        map(
            robots
                .findByIdAndOwnerId(robotId, owner(jwt))
                .orElseThrow(() -> new ResourceNotFoundException("Robot"))));
  }

  private RobotResponse map(Robot r) {
    UUID taskVersion =
        r.getCurrentDeploymentId() == null
            ? null
            : deployments
                .findById(r.getCurrentDeploymentId())
                .map(d -> d.getTaskVersionId())
                .orElse(null);
    var live = runtime.device(r.getJetsonDeviceId(), r.getLastSeenAt());
    return new RobotResponse(
        r.getId(),
        r.getOwnerId(),
        r.getJetsonDeviceId(),
        r.getRobotProfileId(),
        r.getName(),
        live.status(),
        executions.existsByRobotIdAndStatusIn(r.getId(), ACTIVE),
        r.getCurrentDeploymentId(),
        taskVersion,
        live.lastSeenAt(),
        r.getCreatedAt());
  }

  private UUID owner(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
