package com.anssa8bu.ssafy_gpu.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.checkpoint-upload")
public record CheckpointUploadProperties(
    String contentType, Duration requestTimeout, int maxAttempts) {
  public CheckpointUploadProperties {
    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
  }
}
