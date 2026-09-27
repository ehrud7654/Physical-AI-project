package com.anssa8bu.ssafy.jetson.dto;

import java.time.Instant;

public record JetsonHeartbeatResponse(Instant serverTime, int nextHeartbeatSeconds) {}
