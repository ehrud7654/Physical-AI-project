package com.anssa8bu.ssafy.episodevalidation.dto;

import java.time.Instant;
import java.util.Map;

public record EpisodeValidationResult(
    String contractVersion,
    String metaSchemaVersion,
    String summarySchemaVersion,
    String skillKey,
    Boolean success,
    String labelStatus,
    Instant startedAt,
    Instant endedAt,
    long startMonotonicNs,
    long firstTimestampNs,
    long lastTimestampNs,
    int frameCount,
    int poseSampleCount,
    int imuSampleCount,
    int gripperSampleCount,
    int gripperDetectedCount,
    int gripperLongestX,
    int missingFrameCount,
    int trackingGapCount,
    long trackingLostDurationNs,
    String qualityStatus,
    Map<String, Object> device,
    Map<String, Object> camera,
    Map<String, Object> coordinateSystem,
    Map<String, Object> units,
    Map<String, Object> qualityDetails,
    Map<String, Object> manifest,
    Map<String, String> fileSha256,
    long uncompressedSizeBytes,
    int entryCount) {}
