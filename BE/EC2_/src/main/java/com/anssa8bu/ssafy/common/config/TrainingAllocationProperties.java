package com.anssa8bu.ssafy.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.training-allocation")
public record TrainingAllocationProperties(
    int maxActiveJobsPerWorker,
    long minAvailableStorageBytes,
    long minAvailableMemoryMib,
    int maxAttempts,
    Duration recoveryInterval,
    Duration storageNodeOfflineAfter) {
  public TrainingAllocationProperties {
    if (maxActiveJobsPerWorker < 1 || minAvailableStorageBytes < 0
        || minAvailableMemoryMib < 0 || maxAttempts < 1)
      throw new IllegalArgumentException("invalid training allocation limits");
    if (recoveryInterval == null || recoveryInterval.isNegative() || recoveryInterval.isZero()
        || storageNodeOfflineAfter == null || storageNodeOfflineAfter.isNegative()
        || storageNodeOfflineAfter.isZero())
      throw new IllegalArgumentException("training allocation durations must be positive");
  }
}
