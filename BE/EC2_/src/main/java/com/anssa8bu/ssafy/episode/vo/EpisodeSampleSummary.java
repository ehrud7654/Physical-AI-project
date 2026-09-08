package com.anssa8bu.ssafy.episode.vo;

public record EpisodeSampleSummary(
        int frameCount,
        int poseSampleCount,
        int imuSampleCount,
        int gripperSampleCount,
        int missingFrameCount,
        int trackingGapCount,
        long trackingLostDurationNs
) {
    public EpisodeSampleSummary {
        if (frameCount < 1 || poseSampleCount < 1 || imuSampleCount < 1 || gripperSampleCount < 1) {
            throw new IllegalArgumentException("sample counts must be at least one");
        }
        if (missingFrameCount < 0 || trackingGapCount < 0 || trackingLostDurationNs < 0) {
            throw new IllegalArgumentException("quality counters must be zero or positive");
        }
    }
}
