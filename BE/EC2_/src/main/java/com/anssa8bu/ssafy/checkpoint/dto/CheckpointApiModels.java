package com.anssa8bu.ssafy.checkpoint.dto;

import com.anssa8bu.ssafy.model.entity.ActionSpace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class CheckpointApiModels {
  private CheckpointApiModels() {}
  public record Metadata(@NotBlank String architecture, @NotBlank String framework,
      @NotBlank String frameworkVersion, @NotBlank String contractVersion,
      @NotNull UUID robotProfileId, @NotNull ActionSpace actionSpace,
      @NotNull Map<String,Object> actionSpec, @Positive long nParams, @Positive BigDecimal controlRateHz,
      @NotEmpty List<String> cameraNames, @NotNull Map<String,Object> inputSchema,
      @NotNull Map<String,Object> outputSchema) {}
  public record UploadSessionRequest(@Positive int attempt, @NotBlank String leaseToken,
      @NotBlank String filename, @NotNull Instant modelCompletedAt,
      @NotBlank String contentType, @Positive long sizeBytes,
      @Pattern(regexp="[0-9a-f]{64}") String sha256, @NotNull @Valid Metadata metadata) {}
  public record UploadSessionResponse(UUID skillVersionId, int version, String method,
      String uploadUrl, Instant expiresAt, Map<String,String> headers) {}
  public record Checkpoint(@Positive long sizeBytes, @Pattern(regexp="[0-9a-f]{64}") String sha256) {}
  public record CompleteRequest(@Positive int attempt, @NotBlank String leaseToken,
      @NotNull UUID skillVersionId,
      @NotNull @Valid Checkpoint checkpoint, @NotNull @Valid Metadata metadata) {}
  public record CompleteResponse(UUID skillVersionId, String status) {}
}
