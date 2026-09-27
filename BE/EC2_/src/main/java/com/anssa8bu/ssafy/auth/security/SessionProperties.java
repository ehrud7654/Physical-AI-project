package com.anssa8bu.ssafy.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.session")
public record SessionProperties(Duration idleTimeout, Duration absoluteTimeout, boolean cookieSecure) {
  public SessionProperties {
    if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()
        || absoluteTimeout == null || absoluteTimeout.compareTo(idleTimeout) < 0)
      throw new IllegalArgumentException("Invalid login session timeouts");
  }
}
