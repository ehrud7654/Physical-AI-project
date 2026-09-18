package com.anssa8bu.ssafy.jetson.dto;

import com.anssa8bu.ssafy.deployment.entity.DeploymentStatus;
import com.anssa8bu.ssafy.model.entity.ActionSpace;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JetsonDeploymentModels {
  private JetsonDeploymentModels() {}

  public record ReportRequest(
      @NotBlank @Size(max = 150) String reportId,
      @Positive long sequence,
      @NotNull DeploymentStatus status,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
      List<Map<String, Object>> items,
      ErrorInfo error) {}

  public record ErrorInfo(@NotBlank String code, @NotBlank String message) {}

  public record Manifest(
      UUID deploymentId,
      UUID robotId,
      UUID taskVersionId,
      List<Model> models,
      List<JetsonCommand.Step> steps,
      Instant generatedAt) {}

  public record Model(
      UUID skillVersionId,
      String downloadUrl,
      Instant expiresAt,
      long sizeBytes,
      String sha256,
      String architecture,
      String framework,
      String frameworkVersion,
      String contractVersion,
      ActionSpace actionSpace,
      Map<String, Object> actionSpec,
      Map<String, Object> runtimeSpec,
      long nParams,
      BigDecimal controlRateHz,
      List<String> cameraNames,
      Map<String, Object> inputSchema,
      Map<String, Object> outputSchema) {}
}
