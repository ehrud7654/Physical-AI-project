package com.anssa8bu.ssafy.task.presentation;

import static com.anssa8bu.ssafy.task.dto.TaskApiModels.*;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.task.application.TaskService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
  private final TaskService service;
  public TaskController(TaskService service) { this.service = service; }

  @PostMapping
  public ResponseEntity<ApiResponse<TaskResponse>> create(@AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateRequest request) {
    TaskResponse result = service.create(owner(jwt), request);
    return ResponseEntity.created(URI.create("/api/v1/tasks/" + result.id())).body(ApiResponse.of(result));
  }

  @GetMapping
  public ApiResponse<List<TaskResponse>> list(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "false") boolean includeLatestVersion) {
    return ApiResponse.of(service.list(owner(jwt), q, includeLatestVersion));
  }

  @GetMapping("/{taskId}")
  public ApiResponse<TaskResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId,
      @RequestParam(defaultValue = "false") boolean includeLatestVersion) {
    return ApiResponse.of(service.get(owner(jwt), taskId, includeLatestVersion));
  }

  @PatchMapping("/{taskId}")
  public ApiResponse<TaskResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId,
      @Valid @RequestBody UpdateRequest request) {
    return ApiResponse.of(service.update(owner(jwt), taskId, request));
  }

  @PostMapping("/{taskId}/versions")
  public ResponseEntity<ApiResponse<VersionResponse>> createVersion(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID taskId, @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody VersionCreateRequest request) {
    VersionResponse result = service.createVersion(owner(jwt), taskId, idempotencyKey, request);
    return ResponseEntity.created(URI.create("/api/v1/tasks/" + taskId + "/versions/" + result.id()))
        .body(ApiResponse.of(result));
  }

  @GetMapping("/{taskId}/versions")
  public ApiResponse<List<VersionResponse>> listVersions(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID taskId) { return ApiResponse.of(service.listVersions(owner(jwt), taskId)); }

  @GetMapping("/{taskId}/versions/{versionId}")
  public ApiResponse<VersionResponse> getVersion(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID taskId, @PathVariable UUID versionId) {
    return ApiResponse.of(service.getVersion(owner(jwt), taskId, versionId));
  }

  @PostMapping("/{taskId}/versions/{versionId}/validate")
  public ApiResponse<ValidationResponse> validate(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID taskId, @PathVariable UUID versionId,
      @Valid @RequestBody ValidateRequest request) {
    return ApiResponse.of(service.validate(owner(jwt), taskId, versionId, request.robotId()));
  }

  private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
