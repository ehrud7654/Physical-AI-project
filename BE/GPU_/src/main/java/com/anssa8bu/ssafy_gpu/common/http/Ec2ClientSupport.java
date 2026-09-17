package com.anssa8bu.ssafy_gpu.common.http;

import com.anssa8bu.ssafy_gpu.common.exception.Ec2ApiException;
import com.anssa8bu.ssafy_gpu.common.response.ApiErrorResponse;
import java.util.function.Supplier;
import org.springframework.web.client.RestClientResponseException;

public abstract class Ec2ClientSupport {
  protected <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (RestClientResponseException e) {
      try {
        ApiErrorResponse error = e.getResponseBodyAs(ApiErrorResponse.class);
        if (error == null) throw new IllegalStateException("empty error response");
        throw new Ec2ApiException(e.getStatusCode().value(), error.code(), error.message());
      } catch (Ec2ApiException parsed) {
        throw parsed;
      } catch (Exception ignored) {
        throw new Ec2ApiException(
            e.getStatusCode().value(), "EC2_API_ERROR", e.getResponseBodyAsString());
      }
    }
  }
}
