package com.anssa8bu.ssafy.deviceenrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeviceEnrollmentCompleteRequest(
    @NotNull UUID enrollmentId, @NotBlank String registrationToken) {}
