package com.anssa8bu.ssafy_gpu.training.cleanup;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.training-cleanup")
public record TrainingArtifactCleanupProperties(
    boolean enabled, Duration orphanGracePeriod, Duration scanInterval) {}
