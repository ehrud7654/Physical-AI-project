package com.anssa8bu.ssafy.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TaskApiModels {
  private TaskApiModels() {}

  public record CreateRequest(
      @NotBlank @Size(max = 100) String taskKey,
      @NotBlank @Size(max = 150) String displayName,
      String description,
      @Size(min = 1) List<@Valid StepRequest> steps) {}

  public record UpdateRequest(
      @Size(max = 150) String displayName,
      String description,
      @Size(min = 1) List<@Valid StepRequest> steps) {}

  public record StepRequest(
      @Positive int order,
      @NotNull UUID skillVersionId,
      Map<String, Object> parameters,
      @Positive Integer timeoutSeconds) {}

  public record VersionCreateRequest(@NotEmpty List<@Valid StepRequest> steps) {}

  public record StepResponse(
      int order, UUID skillVersionId, Map<String, Object> parameters, Integer timeoutSeconds) {}

  public record VersionResponse(
      UUID id, UUID taskId, int version, List<StepResponse> steps, Instant createdAt) {}

  public record TaskResponse(
      UUID id,
      UUID ownerId,
      String taskKey,
      String displayName,
      String description,
      Integer latestVersion,
      VersionResponse latestVersionDetails,
      Instant createdAt,
      Instant updatedAt) {}

  public record ValidateRequest(@NotNull UUID robotId) {}

  public record ValidationIssue(String code, String message, String recoveryAction) {}

  public record ValidationResponse(
      boolean valid,
      String readiness,
      UUID robotId,
      UUID taskVersionId,
      UUID deploymentId,
      Instant observedAt,
      List<ValidationIssue> blockingReasons,
      List<ValidationIssue> warnings) {}
}
