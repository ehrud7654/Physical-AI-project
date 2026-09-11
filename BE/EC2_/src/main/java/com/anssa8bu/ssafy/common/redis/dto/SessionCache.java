package com.anssa8bu.ssafy.common.redis.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionCache(
    int schemaVersion, UUID sessionId, UUID userId, Instant expiresAt, boolean revoked) {
  public SessionCache {
    if (schemaVersion != 1) throw new IllegalArgumentException();
  }
}
