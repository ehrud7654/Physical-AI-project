package com.anssa8bu.ssafy_gpu.common.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.worker")
public record WorkerProperties(
    String workerId,
    String nodeKey,
    String nodeName,
    Path storageRoot,
    int maxGpuSlots,
    Duration storageHeartbeatInterval,
    boolean claimPollingEnabled,
    Duration claimPollingInitialDelay,
    Duration claimPollingInterval,
    long claimAvailableMemoryMib,
    String gitRevision) {}
