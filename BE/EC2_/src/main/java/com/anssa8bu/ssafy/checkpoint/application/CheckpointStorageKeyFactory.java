package com.anssa8bu.ssafy.checkpoint.application;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CheckpointStorageKeyFactory {
  private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  public String create(UUID ownerId, String skillKey, int version, Instant completedAt) {
    return "ckpt/%s/%s/v%d/%s_%s.ckpt".formatted(ownerId.toString().toLowerCase(),
        skillKey, version, skillKey, TIME.format(completedAt));
  }
}
