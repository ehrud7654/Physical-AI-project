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
      @PositiveOrZero Long availableMemoryMiB,
      @PositiveOrZero Long storageCapacityBytes, @PositiveOrZero Long storageAvailableBytes) {}
  public record ArchiveData(String downloadUrl, Instant expiresAt, String contentType,
      long sizeBytes, String sha256) {}
  public record EpisodeData(UUID episodeId, String sessionId,
      String inputRole, String qualityStatus, String contractVersion, ArchiveData archive) {}
  public record BaseModelData(UUID skillVersionId, int version, String downloadUrl,
      Instant expiresAt, String contentType, long sizeBytes, String sha256,
      String architecture, String framework, String frameworkVersion, String contractVersion) {}
  public record JobData(UUID id, UUID ownerId, String skillKey, TrainingInputMode inputMode,
      TrainingMode trainingMode, BaseModelData baseModel,
      Map<String,Object> datasetQuery, String inputSnapshotSha256, List<EpisodeData> episodes) {}
  public record ClaimResponse(JobData job, int attempt, String leaseToken, Instant leaseExpiresAt,
      int heartbeatIntervalSeconds) {}

  public record HeartbeatRequest(@NotBlank String workerId, UUID storageNodeId,
      @Positive int attempt, @NotBlank String leaseToken, @PositiveOrZero Long storageAvailableBytes) {}
  public record HeartbeatResponse(Instant leaseExpiresAt, boolean cancelRequested,
      Instant serverTime) {}

  public record EventRequest(@Positive int attempt, @NotBlank String leaseToken,
      @NotBlank String eventId, @Positive long sequence,
      @NotBlank String type, @NotNull Instant occurredAt, @NotNull Map<String,Object> payload) {}
  public record EventResponse(boolean accepted, TrainingJobStatus jobStatus, long lastSequence) {}
}
