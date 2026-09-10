package com.anssa8bu.ssafy.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenProvider {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties properties;
  private final Clock clock;

  @Autowired
  public AccessTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
    this(jwtEncoder, properties, Clock.systemUTC());
  }

  AccessTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  public String issue(UUID userId) {
    Instant issuedAt = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long expiresInSeconds() {
    return properties.accessTokenTtl().toSeconds();
  }
}
