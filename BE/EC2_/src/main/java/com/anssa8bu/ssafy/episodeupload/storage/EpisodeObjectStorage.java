package com.anssa8bu.ssafy.episodeupload.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface EpisodeObjectStorage {
  PresignedUpload createUpload(String bucket, String key, long sizeBytes, String sha256, Duration ttl);
  StoredObject inspect(String bucket, String key);
  Optional<StoredObject> inspectIfExists(String bucket, String key);
  InputStream open(String bucket, String key);
  void delete(String bucket, String key);

  record PresignedUpload(String url, String checksumBase64, Instant expiresAt) {}
  record StoredObject(long sizeBytes, String checksumBase64) {}
}
