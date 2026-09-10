package com.anssa8bu.ssafy.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecentTrainingResponse(
    UUID id,
    UUID skillId,
    String skillName,
    UUID umiDeviceId,
    String umiDeviceName,
    String status,
    String stage,
    BigDecimal progressPercent,
    Instant startedAt,
    Instant updatedAt) {}
