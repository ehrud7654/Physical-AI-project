package com.anssa8bu.ssafy.episodeupload.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.episode-storage")
public record EpisodeStorageProperties(
    String bucket, String region, Duration presignTtl, long maxUploadBytes) {
  public EpisodeStorageProperties {
    if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("episode S3 bucket is required");
    if (region == null || region.isBlank()) throw new IllegalArgumentException("AWS region is required");
    if (presignTtl == null || presignTtl.isZero() || presignTtl.isNegative()) throw new IllegalArgumentException("presign TTL must be positive");
    if (maxUploadBytes < 1) throw new IllegalArgumentException("max upload bytes must be positive");
  }
}
