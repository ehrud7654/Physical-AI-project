package com.anssa8bu.ssafy_gpu.storage.dto;

import java.time.Instant;
import java.util.UUID;

public final class StorageNodeModels {
  private StorageNodeModels() {}

  public enum StorageType {
    LOCAL
  }

  public enum StorageNodeStatus {
    ONLINE,
    OFFLINE,
    DRAINING
  }

  public record RegisterRequest(
      String workerId,
      String nodeKey,
      String name,
      StorageType storageType,
      long capacityBytes,
      long availableBytes) {}

  public record NodeResponse(
      UUID id,
      String workerId,
      String nodeKey,
      String name,
      StorageType storageType,
      StorageNodeStatus status,
      long capacityBytes,
      long availableBytes,
      Instant lastSeenAt) {}

  public record HeartbeatRequest(
      String workerId,
      StorageNodeStatus status,
      long capacityBytes,
      long availableBytes,
      int activeUploadCount,
      int activeTrainingCount) {}

  public record HeartbeatResponse(
      Instant serverTime, int nextHeartbeatSeconds, boolean acceptNewUploads) {}
}
