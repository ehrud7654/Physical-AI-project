package com.anssa8bu.ssafy.episodeupload.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EpisodeUploadSessionResponse(
    UUID uploadSessionId,
    UUID sessionId,
    String uploadMethod,
    String uploadUrl,
    Map<String, String> requiredHeaders,
    Instant expiresAt,
    long maxUploadBytes) {}
