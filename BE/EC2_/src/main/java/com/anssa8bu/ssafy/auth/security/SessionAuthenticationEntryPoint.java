package com.anssa8bu.ssafy.auth.security;

import com.anssa8bu.ssafy.common.logging.ApiRequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class SessionAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final BearerTokenAuthenticationEntryPoint bearer =
      new BearerTokenAuthenticationEntryPoint();

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof SessionStoreUnavailableException) {
        ApiRequestLoggingFilter.error(
            request, "AUTH_SESSION_UNAVAILABLE", "로그인 세션 저장소에 연결할 수 없습니다.");
        response.setStatus(503);
        response.setContentType("application/json;charset=UTF-8");
        response
            .getWriter()
            .write(
                "{\"code\":\"AUTH_SESSION_UNAVAILABLE\",\"message\":\"로그인 세션 저장소에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.\"}");
        return;
      }
    }
    ApiRequestLoggingFilter.error(request, "UNAUTHORIZED", "인증 정보가 없거나 토큰이 유효하지 않습니다.");
    bearer.commence(request, response, exception);
  }
}
