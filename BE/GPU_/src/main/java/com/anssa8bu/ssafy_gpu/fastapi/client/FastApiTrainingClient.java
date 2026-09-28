package com.anssa8bu.ssafy_gpu.fastapi.client;

import static com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.*;

import com.anssa8bu.ssafy_gpu.common.response.ApiResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Component
public class FastApiTrainingClient {
  private static final Logger log = LoggerFactory.getLogger(FastApiTrainingClient.class);

  private final RestClient client;
  private final ObjectMapper objectMapper;

  public FastApiTrainingClient(
      @Qualifier("fastApiRestClient") RestClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public CreateRunResponse create(CreateRunRequest request) {
    try {
      String requestJson = objectMapper.writeValueAsString(request);
      log.info(
          "FastAPI 학습 실행 요청 전송: trainingJobId={}, attempt={}, episodes={}, bodyBytes={}",
          request.trainingJobId(),
          request.attempt(),
          request.episodes().size(),
          requestJson.getBytes(StandardCharsets.UTF_8).length);

      ApiResponse<CreateRunResponse> response =
          client
              .post()
              .uri("/internal/training-runs")
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(requestJson)
              .retrieve()
              .body(new ParameterizedTypeReference<>() {});
      return requireData(response);
    } catch (RestClientResponseException exception) {
      throw new IllegalStateException(
          "FastAPI 학습 실행 요청 실패: HTTP "
              + exception.getStatusCode().value()
              + ", body="
              + exception.getResponseBodyAsString(),
          exception);
    }
  }

  public RunResponse get(String runId) {
    ApiResponse<RunResponse> response =
        client
            .get()
            .uri("/internal/training-runs/{runId}", runId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return requireData(response);
  }

  public void cancel(String runId, String reason) {
    client
        .post()
        .uri("/internal/training-runs/{runId}/cancel", runId)
        .body(new CancelRequest(reason))
        .retrieve()
        .toBodilessEntity();
  }

  public void renewLease(String runId, java.time.Instant expiresAt) {
    client
        .patch()
        .uri("/internal/training-runs/{runId}/lease", runId)
        .body(new LeaseRequest(expiresAt))
        .retrieve()
        .toBodilessEntity();
  }

  private static <T> T requireData(ApiResponse<T> response) {
    if (response == null || response.data() == null)
      throw new IllegalStateException("FastAPI 응답 data가 없습니다.");
    return response.data();
  }
}
