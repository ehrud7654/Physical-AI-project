package com.anssa8bu.ssafy.episode.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EpisodeStorageKeyGenerator {

  private static final DateTimeFormatter UTC_PATH_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

  public String generate(UUID ownerId, Instant endedAt, UUID sessionId) {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(endedAt, "endedAt must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    if (sessionId.version() != 4) {
      throw new IllegalArgumentException("sessionId must be a UUID v4");
    }

    return "episodes/"
        + ownerId.toString().toLowerCase(Locale.ROOT)
        + "/"
        + UTC_PATH_TIME.format(endedAt)
        + "/"
        + sessionId.toString().toLowerCase(Locale.ROOT)
        + ".zip";
  }
}
