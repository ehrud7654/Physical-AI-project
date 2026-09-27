package com.anssa8bu.ssafy.auth.application;

import com.anssa8bu.ssafy.auth.security.SessionStoreUnavailableException;
import com.anssa8bu.ssafy.common.redis.dto.SessionCache;
import com.anssa8bu.ssafy.common.redis.repository.SessionCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.anssa8bu.ssafy.auth.security.LoginSessionException;
import com.anssa8bu.ssafy.auth.security.RefreshTokenProvider;
import com.anssa8bu.ssafy.auth.security.SessionProperties;
import java.time.Instant;

@Service
public class LoginSessionService {
  private final SessionCacheRepository sessions;
  private final Clock clock;
  private final RefreshTokenProvider tokens;
  private final SessionProperties properties;

  public LoginSessionService(SessionCacheRepository sessions, Clock clock,
      RefreshTokenProvider tokens, SessionProperties properties) {
    this.sessions = sessions;
    this.clock = clock;
    this.tokens = tokens;
    this.properties = properties;
  }

  public void save(SessionCache session) {
    try {
      Duration ttl = Duration.between(clock.instant(), session.expiresAt());
      if (ttl.isNegative() || ttl.isZero())
        throw new IllegalStateException("Session already expired");
      sessions.save(session.sessionId(), session, ttl);
    } catch (RuntimeException error) {
      throw new SessionStoreUnavailableException(error);
    }
  }

  public Optional<SessionCache> find(UUID id) {
    try {
      return sessions.find(id);
    } catch (RuntimeException error) {
      throw new SessionStoreUnavailableException(error);
    }
  }

  public void delete(UUID id) {
    try {
      sessions.delete(id);
    } catch (RuntimeException error) {
      throw new SessionStoreUnavailableException(error);
    }
  }

  public SessionCache create(UUID userId) {
    Instant now = clock.instant();
    UUID id = UUID.randomUUID();
    Instant absolute = now.plus(properties.absoluteTimeout());
    SessionCache session = new SessionCache(2, id, userId, earlier(now.plus(properties.idleTimeout()), absolute),
        false, now, now, absolute, tokens.hash(tokens.issue(id, 0)), 0, now);
    save(session);
    return session;
  }

  public String refreshToken(SessionCache session) {
    return tokens.issue(session.sessionId(), session.refreshVersion());
  }

  public SessionCache getForRefresh(String token) {
    return requireActive(tokens.parse(token).sessionId(), clock.instant());
  }

  public SessionCache refresh(String token) {
    var parsed = tokens.parse(token);
    for (int retry = 0; retry < 8; retry++) {
      Instant now = clock.instant();
      SessionCache current = requireActive(parsed.sessionId(), now);
      // A bounded replay returns the SAME rotated token, without extending any deadline.
      // This tolerates concurrent tabs and a lost refresh response.
      if (parsed.version() == current.refreshVersion() - 1
          && current.rotatedAt().plusSeconds(10).isAfter(now)) return current;
      if (parsed.version() != current.refreshVersion()
          || !tokens.hash(token).equals(current.refreshTokenHash())) {
        try {
          if (!sessions.deleteMatching(current)) continue;
        } catch (RuntimeException error) { throw new SessionStoreUnavailableException(error); }
        throw new LoginSessionException("REFRESH_TOKEN_REUSED", "로그인 정보가 재사용되어 세션을 종료했습니다.");
      }
      if (current.rotatedAt().plusSeconds(10).isAfter(now)) return current;
      long version = Math.addExact(current.refreshVersion(), 1);
      SessionCache updated = new SessionCache(2, current.sessionId(), current.userId(), current.expiresAt(),
          false, current.createdAt(), current.lastActivityAt(), current.absoluteExpiresAt(),
          tokens.hash(tokens.issue(current.sessionId(), version)), version, now);
      if (replace(current, updated)) return updated;
    }
    throw new SessionStoreUnavailableException(new IllegalStateException("Concurrent session updates"));
  }

  public SessionCache activity(UUID id, UUID userId) {
    for (int retry = 0; retry < 8; retry++) {
      Instant now = clock.instant();
      SessionCache current = requireActive(id, now);
      if (!current.userId().equals(userId)) throw expired();
      Instant expiry = earlier(now.plus(properties.idleTimeout()), current.absoluteExpiresAt());
      SessionCache updated = new SessionCache(2, id, userId, expiry, false,
          current.createdAt(), now, current.absoluteExpiresAt(), current.refreshTokenHash(),
          current.refreshVersion(), current.rotatedAt());
      if (replace(current, updated)) return updated;
    }
    throw new SessionStoreUnavailableException(new IllegalStateException("Concurrent session updates"));
  }

  public void logout(String token) {
    if (token == null || token.isBlank()) return;
    RefreshTokenProvider.Parsed parsed;
    try { parsed = tokens.parse(token); }
    catch (LoginSessionException invalid) { return; }
    // A previously issued token can revoke its own session, even after access-token expiry.
    delete(parsed.sessionId());
  }

  private SessionCache requireActive(UUID id, Instant now) {
    return find(id).filter(s -> s.schemaVersion() == 2 && !s.revoked()
        && s.expiresAt().isAfter(now) && s.absoluteExpiresAt() != null
        && s.absoluteExpiresAt().isAfter(now)).orElseThrow(LoginSessionService::expired);
  }

  private boolean replace(SessionCache current, SessionCache updated) {
    try {
      return sessions.replace(current, updated, Duration.between(clock.instant(), updated.expiresAt()));
    } catch (RuntimeException error) { throw new SessionStoreUnavailableException(error); }
  }

  private static LoginSessionException expired() {
    return new LoginSessionException("AUTH_SESSION_EXPIRED", "로그인 세션이 만료되었습니다.");
  }

  private static Instant earlier(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }
}
