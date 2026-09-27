package com.anssa8bu.ssafy.common.logging;

import com.anssa8bu.ssafy.common.response.ErrorResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseLoggingAdvice implements ResponseBodyAdvice<Object> {
  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (request instanceof ServletServerHttpRequest servlet
        && !MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
      var raw = servlet.getServletRequest();
      if (body instanceof ErrorResponse error) {
        ApiRequestLoggingFilter.error(raw, error.code(), error.message());
      } else if (body instanceof ProblemDetail problem) {
        ApiRequestLoggingFilter.error(raw, "HTTP_" + problem.getStatus(), problem.getDetail());
      } else {
        raw.setAttribute(ApiRequestLoggingFilter.SUMMARY, ApiLogSummary.response(body));
      }
    }
    return body;
  }
}
