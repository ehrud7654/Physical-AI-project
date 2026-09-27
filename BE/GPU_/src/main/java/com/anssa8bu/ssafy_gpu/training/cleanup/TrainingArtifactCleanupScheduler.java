package com.anssa8bu.ssafy_gpu.training.cleanup;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrainingArtifactCleanupScheduler {
  private final TrainingArtifactCleanupService cleanup;

  public TrainingArtifactCleanupScheduler(TrainingArtifactCleanupService cleanup) {
    this.cleanup = cleanup;
  }

  @Scheduled(
      initialDelayString = "${app.training-cleanup.scan-interval:1h}",
      fixedDelayString = "${app.training-cleanup.scan-interval:1h}")
  public void cleanupOrphans() {
    cleanup.cleanupOrphans();
  }
}
