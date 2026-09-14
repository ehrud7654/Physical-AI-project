package com.anssa8bu.ssafy.jetson.dto;

import jakarta.validation.constraints.Size;

public record JetsonHeartbeatRequest(
    @Size(max = 100) String firmwareVersion,
    @Size(max = 100) String runtimeVersion,
    @Size(max = 100) String contractVersion) {}
