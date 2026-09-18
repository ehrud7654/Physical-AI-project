package com.anssa8bu.ssafy_gpu.fastapi.dto;

import com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.Metadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class FastApiTrainingModels {
  private FastApiTrainingModels() {}

  public record EpisodeInput(
      int inputOrder, UUID episodeId, String storageKey, String sha256, String contractVersion) {}

  public record BaseModelInput(
      UUID skillVersionId,
      int version,
      String storageKey,
      String sha256,
      String architecture,
      String framework,
      String frameworkVersion,
      String contractVersion) {}

  public record CreateRunRequest(
      UUID trainingJobId,
      int attempt,
      String workerId,
      UUID storageNodeId,
      int gpuIndex,
      String trainingMode,
      BaseModelInput baseModel,
      String inputSnapshotSha256,
      List<EpisodeInput> episodes,
      Instant executionLeaseExpiresAt) {}

  public record CreateRunResponse(
      String runId, UUID trainingJobId, int attempt, String status) {}

  public record RunError(String code, String message) {}

  public record CheckpointResult(
      String checkpointStorageKey, long sizeBytes, String sha256, Metadata metadata) {}

  public record RunResponse(
      String runId,
      UUID trainingJobId,
      int attempt,
      String status,
      long sequence,
      String stage,
      BigDecimal percent,
      Integer epoch,
      Integer totalEpochs,
      Instant lastProgressAt,
      Instant lastProcessHeartbeatAt,
      Instant modelCompletedAt,
      CheckpointResult result,
      RunError error) {}

  public record CancelRequest(String reason) {}
  public record LeaseRequest(Instant executionLeaseExpiresAt) {}
  public record CompletionRequest(
      String eventId,
      UUID trainingJobId,
      int attempt,
      Instant modelCompletedAt,
      CheckpointResult result) {}
}
