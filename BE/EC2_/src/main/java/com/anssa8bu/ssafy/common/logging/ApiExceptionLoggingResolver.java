package com.anssa8bu.ssafy.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Observe exceptions before the existing handlers resolve them; keep their response contracts. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionLoggingResolver implements HandlerExceptionResolver {
  @Override
  public ModelAndView resolveException(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    request.setAttribute(ApiRequestLoggingFilter.CAUSE, ApiLogSummary.cause(exception));
    if (exception instanceof org.springframework.web.ErrorResponse error) {
      ApiRequestLoggingFilter.error(
          request, "HTTP_" + error.getStatusCode().value(), error.getBody().getDetail());
    }
    return null;
  }
}
