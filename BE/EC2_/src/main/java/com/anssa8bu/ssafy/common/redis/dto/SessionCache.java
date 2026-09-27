package com.anssa8bu.ssafy.common.redis.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionCache(
    int schemaVersion, UUID sessionId, UUID userId, Instant expiresAt, boolean revoked,
    Instant createdAt, Instant lastActivityAt, Instant absoluteExpiresAt,
    String refreshTokenHash, long refreshVersion, Instant rotatedAt) {
  public SessionCache {
    if (schemaVersion != 1 && schemaVersion != 2) throw new IllegalArgumentException();
  }

  // Existing sessions remain valid until their original expiry, but cannot be refreshed.
  public SessionCache(int schemaVersion, UUID sessionId, UUID userId, Instant expiresAt, boolean revoked) {
    this(schemaVersion, sessionId, userId, expiresAt, revoked, null, null, null, null, 0, null);
  }
}
