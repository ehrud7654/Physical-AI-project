package com.anssa8bu.ssafy.common.redis.runtime;

import com.anssa8bu.ssafy.storage.entity.StorageNodeStatus;
import java.time.Instant;
import java.util.UUID;

public record StorageNodeState(
    UUID nodeId,
    String workerId,
    StorageNodeStatus status,
    long capacityBytes,
    long availableBytes,
    int activeUploadCount,
    int activeTrainingCount,
    Instant lastSeenAt) {}
