package com.anssa8bu.ssafy.auth.application;

import com.anssa8bu.ssafy.auth.dto.AuthenticatedUserResponse;
import com.anssa8bu.ssafy.auth.dto.EmailAvailabilityResponse;
import com.anssa8bu.ssafy.auth.dto.LoginRequest;
import com.anssa8bu.ssafy.auth.dto.LoginResponse;
import com.anssa8bu.ssafy.auth.dto.SignupRequest;
import com.anssa8bu.ssafy.auth.security.AccessTokenProvider;
import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.InvalidCredentialsException;
import com.anssa8bu.ssafy.common.redis.dto.SessionCache;
import com.anssa8bu.ssafy.user.entity.User;
import com.anssa8bu.ssafy.user.entity.UserStatus;
import com.anssa8bu.ssafy.user.repository.UserRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenProvider accessTokenProvider;
  private final LoginSessionService sessions;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AccessTokenProvider accessTokenProvider,
      LoginSessionService sessions,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.accessTokenProvider = accessTokenProvider;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public LoginResult login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    User user =
        userRepository.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);

    if (user.getStatus() != UserStatus.ACTIVE
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    return issue(user, sessions.create(user.getId()));
  }

  @Transactional(readOnly = true)
  public LoginResult refresh(String token) {
    SessionCache session = sessions.getForRefresh(token);
    User user = userRepository.findById(session.userId()).filter(u -> u.getStatus() == UserStatus.ACTIVE)
        .orElseGet(() -> {
          sessions.delete(session.sessionId());
          throw new com.anssa8bu.ssafy.auth.security.LoginSessionException(
              "AUTH_SESSION_EXPIRED", "로그인할 수 없는 계정입니다.");
        });
    return issue(user, sessions.refresh(token));
  }

  private LoginResult issue(User user, SessionCache session) {
    var issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    var expiresAt = issuedAt.plusSeconds(accessTokenProvider.expiresInSeconds());
    if (expiresAt.isAfter(session.expiresAt())) expiresAt = session.expiresAt();
    String token = accessTokenProvider.issue(user.getId(), session.sessionId(), issuedAt, expiresAt);
    return new LoginResult(new LoginResponse(token, "Bearer",
        java.time.Duration.between(issuedAt, expiresAt).toSeconds(), AuthenticatedUserResponse.from(user),
        session.expiresAt(), session.absoluteExpiresAt()), sessions.refreshToken(session));
  }

  public record LoginResult(LoginResponse response, String refreshToken) {}

  public void logout(String token) { sessions.logout(token); }

  public SessionCache activity(UUID sessionId, UUID userId) { return sessions.activity(sessionId, userId); }

  public void logout(UUID sessionId) {
    sessions.delete(sessionId);
  }

  @Transactional(readOnly = true)
  public EmailAvailabilityResponse emailAvailability(String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    return new EmailAvailabilityResponse(
        normalized, !userRepository.existsByEmailIgnoreCase(normalized));
  }

  @Transactional
  public AuthenticatedUserResponse signup(SignupRequest request) {
    if (userRepository.existsByEmailIgnoreCase(request.email())) throw duplicateEmail();
    User user =
        new User(
            UUID.randomUUID(),
            request.email(),
            passwordEncoder.encode(request.password()),
            request.name(),
            UserStatus.ACTIVE);
    try {
      return AuthenticatedUserResponse.from(userRepository.saveAndFlush(user));
    } catch (DataIntegrityViolationException error) {
      // The unique index is authoritative when concurrent signups both pass the precheck.
      for (Throwable cause = error; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && "uq_users_email_lower".equals(violation.getConstraintName())) {
          throw duplicateEmail();
        }
      }
      throw error;
    }
  }

  private BusinessConflictException duplicateEmail() {
    return new BusinessConflictException("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
  }
}
