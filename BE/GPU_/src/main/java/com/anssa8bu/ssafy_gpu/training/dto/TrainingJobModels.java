package com.anssa8bu.ssafy_gpu.training.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class TrainingJobModels {
  private TrainingJobModels() {}

  public enum TrainingInputMode {
    EXISTING_EPISODES
  }

  public enum TrainingJobStatus {
    QUEUED,
    PREPARING,
    RUNNING,
    MODEL_UPLOADING,
    MODEL_VALIDATING,
    CANCEL_REQUESTED,
    CANCELED,
    SUCCEEDED,
    FAILED
  }

  public enum TrainingEventType {
    STARTED,
    PROGRESS,
    TRAINING_FINISHED,
    FAILED,
    CANCELED
  }

  public record ClaimRequest(
      String workerId,
      Integer gpuIndex,
      String gitRev,
      UUID storageNodeId,
      Set<String> supportedAlgorithms,
      Long availableMemoryMiB,
      Long storageCapacityBytes,
      Long storageAvailableBytes) {}

  public record TrainingProfileData(
      String profileKey,
      int profileVersion,
      String algorithm,
      Map<String, Object> hyperparameters,
      String configSha256) {}

  public record ArchiveData(
      String downloadUrl, Instant expiresAt, String contentType, long sizeBytes, String sha256) {}

  public record EpisodeData(
      UUID episodeId,
      String sessionId,
      String qualityStatus,
      String contractVersion,
      ArchiveData archive) {}

  public record JobData(
      UUID id,
      String skillKey,
      TrainingInputMode inputMode,
      TrainingProfileData trainingProfile,
      Map<String, Object> datasetQuery,
      String inputSnapshotSha256,
      List<EpisodeData> episodes) {}

  public record ClaimResponse(
      JobData job,
      int attempt,
      String leaseToken,
      Instant leaseExpiresAt,
      int heartbeatIntervalSeconds) {}

  public record HeartbeatRequest(
      String workerId,
      UUID storageNodeId,
      int attempt,
      String leaseToken,
      Long storageAvailableBytes) {}

  public record HeartbeatResponse(
      Instant leaseExpiresAt, boolean cancelRequested, Instant serverTime) {}

  public record EventRequest(
      int attempt,
      String leaseToken,
      String eventId,
      long sequence,
      String type,
      Instant occurredAt,
      Map<String, Object> payload) {}

  public record EventResponse(boolean accepted, TrainingJobStatus jobStatus, long lastSequence) {}

  public static Map<String, Object> progressPayload(
      String stage,
      BigDecimal percent,
      Integer epoch,
      Integer totalEpochs,
      Map<String, Object> metrics) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("stage", stage);
    value.put("percent", percent);
    if (epoch != null) value.put("epoch", epoch);
    if (totalEpochs != null) value.put("totalEpochs", totalEpochs);
    if (metrics != null && !metrics.isEmpty()) value.put("metrics", metrics);
    return Map.copyOf(value);
  }
}
