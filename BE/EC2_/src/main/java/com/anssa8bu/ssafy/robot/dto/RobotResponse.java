package com.anssa8bu.ssafy.robot.dto;

import com.anssa8bu.ssafy.robot.entity.*;
import java.time.Instant;
import java.util.UUID;

public record RobotResponse(
    UUID id,
    UUID ownerId,
    UUID jetsonDeviceId,
    UUID robotProfileId,
    String name,
    RobotConnectionStatus connectionStatus,
    boolean busy,
    UUID currentDeploymentId,
    UUID currentTaskVersionId,
    Instant lastSeenAt,
    Instant createdAt) {}
