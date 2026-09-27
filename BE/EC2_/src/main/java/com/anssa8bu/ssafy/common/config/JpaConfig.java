package com.anssa8bu.ssafy.common.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

  @Bean
  public Clock systemClock() {
    return Clock.systemUTC();
  }

  @Bean
  public DateTimeProvider auditingDateTimeProvider(Clock systemClock) {
    return () -> Optional.of(Instant.now(systemClock));
  }
}
