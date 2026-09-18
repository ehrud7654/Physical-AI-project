package com.anssa8bu.ssafy.deviceenrollment.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceEnrollmentStartResponse(
    UUID enrollmentId, UUID deviceId, String qrPayload, Instant expiresAt) {}
