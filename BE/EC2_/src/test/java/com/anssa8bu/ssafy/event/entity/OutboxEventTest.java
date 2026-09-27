package com.anssa8bu.ssafy.event.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OutboxEventTest {
  private OutboxEvent event() {
    return new OutboxEvent(UUID.randomUUID(), "e", "JOB", "1", "DONE", Map.of(), Instant.EPOCH);
  }

  @Test
  void transitionsAndOwnership() {
    var e = event();
    e.claim("p1", Instant.EPOCH, Duration.ofSeconds(30));
    assertThatThrownBy(() -> e.markPublished("p2", Instant.EPOCH))
        .isInstanceOf(IllegalStateException.class);
    e.markPublished("p1", Instant.EPOCH);
    assertThat(e.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void redactsSecrets() {
    var e = event();
    e.claim("p", Instant.EPOCH, Duration.ofSeconds(1));
    e.reschedule("p", Instant.EPOCH.plusSeconds(1), "token=secret failed");
    assertThat(e.getLastError()).doesNotContain("secret");
  }
}
