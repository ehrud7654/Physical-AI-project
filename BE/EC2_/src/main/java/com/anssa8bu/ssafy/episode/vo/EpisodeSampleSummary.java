package com.anssa8bu.ssafy.episode.vo;

public record EpisodeSampleSummary(
    int frameCount,
    int poseSampleCount,
    int imuSampleCount,
    int gripperSampleCount,
    int missingFrameCount,
    int trackingGapCount,
    long trackingLostDurationNs) {
  public EpisodeSampleSummary {
    if (frameCount < 1 || imuSampleCount < 1 || poseSampleCount < 0 || gripperSampleCount < 0) {
      throw new IllegalArgumentException(
          "frame and IMU counts must be positive; derived counts may be zero");
    }
    if (missingFrameCount < 0 || trackingGapCount < 0 || trackingLostDurationNs < 0) {
      throw new IllegalArgumentException("quality counters must be zero or positive");
    }
  }
}
