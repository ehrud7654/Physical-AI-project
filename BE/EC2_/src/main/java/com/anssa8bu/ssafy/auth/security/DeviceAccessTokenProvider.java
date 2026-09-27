package com.anssa8bu.ssafy.auth.security;

import com.anssa8bu.ssafy.device.entity.Device;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class DeviceAccessTokenProvider {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties properties;
  private final Clock clock;

  public DeviceAccessTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = Clock.systemUTC();
  }

  public String issue(Device device) {
    Instant issuedAt = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(device.getOwnerId().toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
            .claim("scope", "device")
            .claim("actorType", "DEVICE")
            .claim("deviceId", device.getId().toString())
            .claim("deviceType", device.getDeviceType().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long expiresInSeconds() {
    return properties.accessTokenTtl().toSeconds();
  }
}
