package com.anssa8bu.ssafy.storage.dto.internal;

import com.anssa8bu.ssafy.storage.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class StorageNodeApiModels {
  private StorageNodeApiModels() {}
  public record RegisterRequest(@NotBlank String workerId, @NotBlank String nodeKey,
      @NotBlank String name, @NotNull StorageType storageType,
      @PositiveOrZero long capacityBytes, @PositiveOrZero long availableBytes) {}
  public record NodeResponse(UUID id, String workerId, String nodeKey, String name,
      StorageType storageType, StorageNodeStatus status, long capacityBytes,
      long availableBytes, Instant lastSeenAt) {}
  public record HeartbeatRequest(@NotBlank String workerId, @NotNull StorageNodeStatus status,
      @PositiveOrZero long capacityBytes, @PositiveOrZero long availableBytes,
      @PositiveOrZero int activeUploadCount, @PositiveOrZero int activeTrainingCount) {}
  public record HeartbeatResponse(Instant serverTime, int nextHeartbeatSeconds,
      boolean acceptNewUploads) {}
}
