package com.anssa8bu.ssafy.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardRobotResponse(
    UUID id,
    String name,
    DashboardRobotStatus status,
    Instant lastSeenAt,
    UUID currentDeploymentId) {}
