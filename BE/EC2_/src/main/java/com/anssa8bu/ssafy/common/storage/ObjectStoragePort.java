package com.anssa8bu.ssafy.common.storage;

import java.time.Duration;
import java.time.Instant;

public interface ObjectStoragePort {
  PresignedObject presignPut(String bucket, String key, String contentType, long sizeBytes,
      String sha256, Duration ttl);
  PresignedObject presignGet(String bucket, String key, Duration ttl);
  StoredObject inspect(String bucket, String key);
  byte[] readPrefix(String bucket, String key, int length);

  record PresignedObject(String url, Instant expiresAt) {}
  record StoredObject(long sizeBytes, String checksumSha256Base64) {}
}
