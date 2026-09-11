package com.anssa8bu.ssafy.common.redis.dto;

import java.time.Instant;
import java.util.UUID;

public record TrainingLeaseCache(
    int schemaVersion,
    UUID jobId,
    int attempt,
    String workerId,
    String leaseTokenHash,
    Instant leaseExpiresAt) {
  public TrainingLeaseCache {
    if (schemaVersion != 1 || attempt < 1) throw new IllegalArgumentException();
  }
}
