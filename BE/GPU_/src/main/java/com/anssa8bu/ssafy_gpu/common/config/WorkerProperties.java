package com.anssa8bu.ssafy_gpu.common.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.worker")
public record WorkerProperties(
    String workerId,
    String nodeKey,
    String nodeName,
    Path storageRoot,
    Set<String> supportedAlgorithms,
    int maxGpuSlots,
    Duration storageHeartbeatInterval) {}
