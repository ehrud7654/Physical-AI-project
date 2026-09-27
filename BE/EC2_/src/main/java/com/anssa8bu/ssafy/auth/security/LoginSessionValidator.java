package com.anssa8bu.ssafy.auth.security;

import com.anssa8bu.ssafy.auth.application.LoginSessionService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class LoginSessionValidator implements OAuth2TokenValidator<Jwt> {
  private final LoginSessionService sessions;
  private final Clock clock;

  public LoginSessionValidator(LoginSessionService sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    // Only actual device tokens bypass user-session validation.
    if ("DEVICE".equals(jwt.getClaimAsString("actorType"))
        && "device".equals(jwt.getClaimAsString("scope"))) {
      return OAuth2TokenValidatorResult.success();
    }
    if (!"USER".equals(jwt.getClaimAsString("actorType"))) return invalid();
    UUID sessionId;
    UUID userId;
    try {
      sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
      userId = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException error) {
      return invalid();
    }
    return sessions
            .find(sessionId)
            .filter(session -> sessionId.equals(session.sessionId()))
            .filter(session -> userId.equals(session.userId()))
            .filter(session -> !session.revoked())
            .filter(session -> session.schemaVersion() == 1 ||
                session.absoluteExpiresAt() != null && session.absoluteExpiresAt().isAfter(clock.instant()))
            .filter(
                session ->
                    session.expiresAt() != null && session.expiresAt().isAfter(clock.instant()))
            .isPresent()
        ? OAuth2TokenValidatorResult.success()
        : invalid();
  }

  private OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "로그인 세션이 만료되었거나 로그아웃되었습니다.", null));
  }
}
