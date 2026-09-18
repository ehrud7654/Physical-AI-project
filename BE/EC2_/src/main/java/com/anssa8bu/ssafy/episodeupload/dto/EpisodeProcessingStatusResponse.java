package com.anssa8bu.ssafy.episodeupload.dto;

import java.util.UUID;

public record EpisodeProcessingStatusResponse(
    UUID episodeId,
    UUID sessionId,
    String status,
    String qualityStatus,
    ProcessingError error) {

  public record ProcessingError(String code, String message, String recoveryAction) {}
}
