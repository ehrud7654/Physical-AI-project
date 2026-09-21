package com.anssa8bu.ssafy.auth.application;

import com.anssa8bu.ssafy.auth.security.SessionStoreUnavailableException;
import com.anssa8bu.ssafy.common.redis.dto.SessionCache;
import com.anssa8bu.ssafy.common.redis.repository.SessionCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LoginSessionService {
  private final SessionCacheRepository sessions;
  private final Clock clock;

  public LoginSessionService(SessionCacheRepository sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
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
}
