package com.anssa8bu.ssafy_gpu.fastapi.client;

import static com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.*;

import com.anssa8bu.ssafy_gpu.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FastApiTrainingClient {
  private final RestClient client;

  public FastApiTrainingClient(@Qualifier("fastApiRestClient") RestClient client) {
    this.client = client;
  }

  public CreateRunResponse create(CreateRunRequest request) {
    ApiResponse<CreateRunResponse> response =
        client.post().uri("/internal/training-runs").body(request).retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return requireData(response);
  }

  public RunResponse get(String runId) {
    ApiResponse<RunResponse> response =
        client.get().uri("/internal/training-runs/{runId}", runId).retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return requireData(response);
  }

  public void cancel(String runId, String reason) {
    client.post().uri("/internal/training-runs/{runId}/cancel", runId)
        .body(new CancelRequest(reason)).retrieve().toBodilessEntity();
  }

  public void renewLease(String runId, java.time.Instant expiresAt) {
    client.patch().uri("/internal/training-runs/{runId}/lease", runId)
        .body(new LeaseRequest(expiresAt)).retrieve().toBodilessEntity();
  }

  private static <T> T requireData(ApiResponse<T> response) {
    if (response == null || response.data() == null)
      throw new IllegalStateException("FastAPI 응답 data가 없습니다.");
    return response.data();
  }
}
