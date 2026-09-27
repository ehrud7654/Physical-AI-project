package com.anssa8bu.ssafy.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SessionCookieService {
  public static final String NAME = "ssafy_refresh";
  private final SessionProperties properties;
  private final List<String> origins;
  private final Clock clock;
  public SessionCookieService(SessionProperties properties, Clock clock,
      @Value("${app.cors.allowed-origins}") String origins) {
    this.properties = properties;
    this.clock = clock;
    this.origins = Arrays.stream(origins.split(",")).map(String::trim).toList();
  }
  public String read(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    return Arrays.stream(request.getCookies()).filter(c -> NAME.equals(c.getName()))
        .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
  }
  public String issue(String value, Instant expiresAt) {
    return cookie(value, Duration.ofSeconds(Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds())));
  }
  public String clear() { return cookie("", Duration.ZERO); }
  private String cookie(String value, Duration ttl) {
    return ResponseCookie.from(NAME, value).httpOnly(true).secure(properties.cookieSecure())
        .sameSite("Lax").path("/api/v1/auth").maxAge(ttl).build().toString();
  }
  public void checkOrigin(HttpServletRequest request, boolean requireHeader) {
    String origin = request.getHeader("Origin");
    if ((requireHeader && !"1".equals(request.getHeader("X-Session-Request")))
        || (origin != null && !origins.contains(origin))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 세션 요청입니다.");
    }
  }
}
