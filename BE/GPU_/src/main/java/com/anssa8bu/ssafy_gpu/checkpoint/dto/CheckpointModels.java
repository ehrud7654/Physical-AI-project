package com.anssa8bu.ssafy_gpu.checkpoint.dto;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class CheckpointModels {
  private CheckpointModels() {}

  public enum ActionSpace {
    JOINT_ABSOLUTE,
    JOINT_DELTA,
    EEF_RELATIVE_ROT6D
  }

  public record Metadata(
      String architecture,
      String framework,
      String frameworkVersion,
      String contractVersion,
      UUID robotProfileId,
      ActionSpace actionSpace,
      Map<String, Object> actionSpec,
      long nParams,
      BigDecimal controlRateHz,
      List<String> cameraNames,
      Map<String, Object> inputSchema,
      Map<String, Object> outputSchema) {}

  public record UploadSessionRequest(
      int attempt,
      String leaseToken,
      String filename,
      Instant modelCompletedAt,
      String contentType,
      long sizeBytes,
      String sha256,
      Metadata metadata) {}

  public record UploadSessionResponse(
      UUID skillVersionId,
      int version,
      String method,
      String uploadUrl,
      Instant expiresAt,
      Map<String, String> headers) {}

  public record Checkpoint(long sizeBytes, String sha256) {}

  public record CompleteRequest(
      int attempt,
      String leaseToken,
      UUID skillVersionId,
      Checkpoint checkpoint,
      Metadata metadata) {}

  public record CompleteResponse(UUID skillVersionId, String status) {}

  public record LocalCheckpoint(Path path, Instant modelCompletedAt, Metadata metadata) {}

  public record UploadResult(
      UUID skillVersionId,
      int version,
      long sizeBytes,
      String sha256,
      CompleteResponse completion) {}
}
