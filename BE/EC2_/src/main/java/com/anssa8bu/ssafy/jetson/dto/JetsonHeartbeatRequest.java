package com.anssa8bu.ssafy.jetson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JetsonHeartbeatRequest(
    @NotBlank @Size(max = 100) String firmwareVersion,
    @NotBlank @Size(max = 100) String runtimeVersion,
    @NotBlank @Size(max = 100) String contractVersion) {}
