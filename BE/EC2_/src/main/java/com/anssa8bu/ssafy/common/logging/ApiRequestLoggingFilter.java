package com.anssa8bu.ssafy.common.logging;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
  static final String SUMMARY = ApiRequestLoggingFilter.class.getName() + ".summary";
  static final String CAUSE = ApiRequestLoggingFilter.class.getName() + ".cause";
  private static final String CODE = ApiRequestLoggingFilter.class.getName() + ".code";
  private static final String REASON = ApiRequestLoggingFilter.class.getName() + ".reason";

  public static void error(HttpServletRequest request, String code, String reason) {
    request.setAttribute(CODE, ApiLogSummary.safeText(code, 80));
    request.setAttribute(REASON, ApiLogSummary.safeText(reason, 300));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !path.startsWith("/api/") && !path.startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    String requestId = UUID.randomUUID().toString();
    String previousId = MDC.get("requestId");
    MDC.put("requestId", requestId);
    response.setHeader("X-Request-Id", requestId);
    AtomicBoolean logged = new AtomicBoolean();
    HttpServletResponseWrapper observed =
        new HttpServletResponseWrapper(response) {
          @Override
          public void sendError(int status, String message) throws IOException {
            if (request.getAttribute(CODE) == null) error(request, "HTTP_" + status, message);
            super.sendError(status, message);
          }
        };
    boolean failed = false;
    try {
      chain.doFilter(request, observed);
    } catch (IOException | ServletException | RuntimeException exception) {
      failed = true;
      request.setAttribute(CAUSE, ApiLogSummary.cause(exception));
      write(request, response, requestId, started, logged, true);
      throw exception;
    } finally {
      try {
        if (!failed && request.isAsyncStarted()) {
          AsyncListener listener =
              new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                  write(request, response, requestId, started, logged, false);
                }

                @Override
                public void onError(AsyncEvent event) {
                  if (event.getThrowable() != null) {
                    request.setAttribute(CAUSE, ApiLogSummary.cause(event.getThrowable()));
                  }
                  write(request, response, requestId, started, logged, true);
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                  error(request, "ASYNC_TIMEOUT", "비동기 응답 대기 시간이 초과되었습니다.");
                  write(request, response, requestId, started, logged, true);
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                  event.getAsyncContext().addListener(this);
                }
              };
          try {
            request.getAsyncContext().addListener(listener);
            log.info(
                "API_ASYNC_STARTED requestId={} method={} path={} status={}",
                requestId,
                request.getMethod(),
                path(request),
                response.getStatus());
          } catch (IllegalStateException completed) {
            write(request, response, requestId, started, logged, false);
          }
        } else if (!failed) {
          write(request, response, requestId, started, logged, false);
        }
      } finally {
        if (previousId == null) MDC.remove("requestId");
        else MDC.put("requestId", previousId);
      }
    }
  }

  private static String path(HttpServletRequest request) {
    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return ApiLogSummary.safeText(route == null ? request.getRequestURI() : route.toString(), 200);
  }

  private static void write(
      HttpServletRequest request,
      HttpServletResponse response,
      String requestId,
      long started,
      AtomicBoolean logged,
      boolean failure) {
    if (!logged.compareAndSet(false, true)) return;
    int status =
        failure && !response.isCommitted() && response.getStatus() < 400
            ? 500
            : response.getStatus();
    long duration = (System.nanoTime() - started) / 1_000_000;
    if (failure || status >= 400) {
      Object code = request.getAttribute(CODE);
      Object reason = request.getAttribute(REASON);
      Object cause = request.getAttribute(CAUSE);
      HttpStatus known = HttpStatus.resolve(status);
      if (code == null)
        code =
            failure || status >= 500
                ? "INTERNAL_SERVER_ERROR"
                : status == 403 ? "FORBIDDEN" : "HTTP_" + status;
      if (reason == null)
        reason =
            failure
                ? "요청 처리 중 예외가 발생했습니다."
                : known == null ? "요청 처리에 실패했습니다." : known.getReasonPhrase();
      String message =
          "API_ERROR requestId={} method={} path={} status={} durationMs={} code={} reason={} cause={}";
      if (failure || status >= 500) {
        log.error(
            message,
            requestId,
            request.getMethod(),
            path(request),
            status,
            duration,
            code,
            reason,
            cause == null ? "none" : cause);
      } else {
        log.warn(
            message,
            requestId,
            request.getMethod(),
            path(request),
            status,
            duration,
            code,
            reason,
            cause == null ? "none" : cause);
      }
    } else {
      Object summary = request.getAttribute(SUMMARY);
      log.info(
          "API_SUCCESS requestId={} method={} path={} status={} durationMs={} data={}",
          requestId,
          request.getMethod(),
          path(request),
          status,
          duration,
          summary == null ? "empty" : summary);
    }
  }
}
