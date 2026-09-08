package com.anssa8bu.ssafy.episode.vo;

import java.time.Instant;
import java.util.Objects;

public record EpisodeTiming(
    Instant startedAt,
    Instant endedAt,
    long startMonotonicNs,
    long firstTimestampNs,
    long lastTimestampNs) {
  public EpisodeTiming {
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    Objects.requireNonNull(endedAt, "endedAt must not be null");
    if (endedAt.isBefore(startedAt))
      throw new IllegalArgumentException("endedAt must not be before startedAt");
    if (startMonotonicNs < 0)
      throw new IllegalArgumentException("startMonotonicNs must be zero or positive");
    if (firstTimestampNs < startMonotonicNs)
      throw new IllegalArgumentException("firstTimestampNs must not be before startMonotonicNs");
    if (lastTimestampNs < firstTimestampNs)
      throw new IllegalArgumentException("lastTimestampNs must not be before firstTimestampNs");
  }

  public long durationNs() {
    return lastTimestampNs - firstTimestampNs;
  }
}
