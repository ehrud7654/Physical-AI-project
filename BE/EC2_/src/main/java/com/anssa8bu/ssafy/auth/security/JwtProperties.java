package com.anssa8bu.ssafy.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

  public JwtProperties {
    if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("app.auth.jwt.secret must be at least 32 bytes");
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("app.auth.jwt.issuer must not be blank");
    }
    if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
      throw new IllegalArgumentException("app.auth.jwt.access-token-ttl must be positive");
    }
  }
}
