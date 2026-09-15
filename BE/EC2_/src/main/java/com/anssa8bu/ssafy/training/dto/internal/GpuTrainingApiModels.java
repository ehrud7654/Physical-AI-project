package com.anssa8bu.ssafy.training.dto.internal;

import com.anssa8bu.ssafy.training.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class GpuTrainingApiModels {
  private GpuTrainingApiModels() {}

  public record ClaimRequest(@NotBlank String workerId, @NotNull @PositiveOrZero Integer gpuIndex,
      String gitRev, @NotNull UUID storageNodeId, String ingestBaseUrl,
      @NotEmpty Set<String> supportedAlgorithms, @PositiveOrZero Long availableMemoryMiB,
      @PositiveOrZero Long storageCapacityBytes, @PositiveOrZero Long storageAvailableBytes) {}
  public record TrainingProfileData(String profileKey, int profileVersion, String algorithm,
      Map<String,Object> hyperparameters, String configSha256) {}
  public record ArchiveData(String downloadUrl, Instant expiresAt, String contentType,
      long sizeBytes, String sha256) {}
  public record EpisodeData(UUID episodeId, String sessionId, String qualityStatus,
      ArchiveData archive) {}
  public record JobData(UUID id, String skillKey, TrainingInputMode inputMode,
      TrainingProfileData trainingProfile, Map<String,Object> datasetQuery,
      String inputSnapshotSha256, List<EpisodeData> episodes) {}
  public record ClaimResponse(JobData job, int attempt, String leaseToken, Instant leaseExpiresAt,
      int heartbeatIntervalSeconds) {}

  public record HeartbeatRequest(@NotBlank String workerId, UUID storageNodeId,
      @Positive int attempt, @NotBlank String leaseToken, @PositiveOrZero Long storageAvailableBytes) {}
  public record HeartbeatResponse(Instant leaseExpiresAt, boolean cancelRequested,
      boolean closeInputRequested, Instant serverTime) {}

  public record EventRequest(@Positive int attempt, @NotBlank String leaseToken,
      @NotBlank String eventId, @Positive long sequence,
      @NotBlank String type, @NotNull Instant occurredAt, @NotNull Map<String,Object> payload) {}
  public record EventResponse(boolean accepted, TrainingJobStatus jobStatus, long lastSequence) {}
}
