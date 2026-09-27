package com.anssa8bu.ssafy.deviceenrollment.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceEnrollmentCompleteResponse(
    UUID deviceId, String deviceSecret, Instant enrolledAt) {}
