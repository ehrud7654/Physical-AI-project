package com.anssa8bu.ssafy.episodeupload.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EpisodeUploadSessionRequest(
    @NotNull UUID sessionId,
    @NotBlank @Pattern(regexp = "application/zip") String contentType,
    @Positive long sizeBytes,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sha256,
    @NotNull @Valid Metadata metadata,
    @NotNull @Valid Summary summary) {

  public record Metadata(
      @NotBlank String contractVersion,
      @NotBlank String schemaVersion,
      @NotBlank String skillKey,
      @NotBlank @Pattern(regexp = "REAL") String source,
      Boolean success,
      @NotBlank @Pattern(regexp = "LABELED|UNLABELED") String labelStatus,
      @NotNull Instant startedAt,
      @NotNull Instant endedAt,
      @PositiveOrZero long startMonotonicNs,
      @NotBlank @Pattern(regexp = "MONOTONIC_NS") String timestampTimebase,
      @NotNull Map<String, Object> device,
      @NotNull Map<String, Object> camera,
      Map<String, Object> coordinateSystem,
      Map<String, Object> units) {}

  public record Summary(
      @NotBlank String schemaVersion,
      @PositiveOrZero long firstTimestampNs,
      @PositiveOrZero long lastTimestampNs,
      @Positive int frameCount,
      @Positive int poseSampleCount,
      @Positive int imuSampleCount,
      @Positive int gripperSampleCount,
      @PositiveOrZero int missingFrameCount,
      @PositiveOrZero int trackingGapCount,
      @PositiveOrZero long trackingLostDurationNs) {}
}
