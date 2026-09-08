package com.anssa8bu.ssafy.episode.vo;

import com.anssa8bu.ssafy.episode.entity.EpisodeSource;

import java.util.Objects;
import java.util.UUID;

public record EpisodeIdentity(
        UUID ownerId,
        UUID umiDeviceId,
        UUID storageNodeId,
        UUID retentionPolicyId,
        UUID skillId,
        String sessionId,
        EpisodeSource source
) {
    public EpisodeIdentity {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(umiDeviceId, "umiDeviceId must not be null");
        Objects.requireNonNull(storageNodeId, "storageNodeId must not be null");
        Objects.requireNonNull(retentionPolicyId, "retentionPolicyId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }
}
