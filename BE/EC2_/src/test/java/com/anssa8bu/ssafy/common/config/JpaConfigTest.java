package com.anssa8bu.ssafy.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JpaConfigTest {

  @Test
  void auditingUsesInjectedUtcClock() {
    Instant expected = Instant.parse("2026-09-07T05:00:00Z");
    Clock fixedClock = Clock.fixed(expected, ZoneOffset.UTC);

    Object auditingTime =
        new JpaConfig().auditingDateTimeProvider(fixedClock).getNow().orElseThrow();

    assertThat(auditingTime).isEqualTo(expected);
  }
}
