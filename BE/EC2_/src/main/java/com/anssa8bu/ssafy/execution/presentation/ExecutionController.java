package com.anssa8bu.ssafy.execution.presentation;

import static com.anssa8bu.ssafy.execution.dto.ExecutionApiModels.CreateRequest;
import static com.anssa8bu.ssafy.execution.dto.ExecutionApiModels.ExecutionResponse;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.execution.application.ExecutionService;
import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import com.anssa8bu.ssafy.jetson.application.JetsonCommandFactory;
import com.anssa8bu.ssafy.jetson.application.JetsonCommandStreamService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/robots/{robotId}/executions")
public class ExecutionController {
  private final ExecutionService service;
  private final JetsonCommandFactory commands;
  private final JetsonCommandStreamService streams;

  public ExecutionController(
      ExecutionService service, JetsonCommandFactory commands, JetsonCommandStreamService streams) {
    this.service = service;
    this.commands = commands;
    this.streams = streams;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ExecutionResponse>> create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID robotId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateRequest request) {
    ExecutionResponse response = service.create(owner(jwt), robotId, idempotencyKey, request);
    streams.publish(commands.executionStart(response));
    return ResponseEntity.accepted().body(ApiResponse.of(response));
  }

  @GetMapping
  public ApiResponse<List<ExecutionResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID robotId) {
    return ApiResponse.of(service.list(owner(jwt), robotId));
  }

  @GetMapping("/{executionId}")
  public ApiResponse<ExecutionResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID robotId,
      @PathVariable UUID executionId) {
    return ApiResponse.of(service.get(owner(jwt), robotId, executionId));
  }

  @PostMapping("/{executionId}/cancel")
  public ResponseEntity<ApiResponse<ExecutionResponse>> cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID robotId,
      @PathVariable UUID executionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    ExecutionResponse response = service.cancel(owner(jwt), robotId, executionId, idempotencyKey);
    if (response.status() == RobotExecutionStatus.CANCEL_REQUESTED) {
      streams.publish(commands.executionCancel(response));
    }
    return ResponseEntity.accepted().body(ApiResponse.of(response));
  }

  private UUID owner(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
