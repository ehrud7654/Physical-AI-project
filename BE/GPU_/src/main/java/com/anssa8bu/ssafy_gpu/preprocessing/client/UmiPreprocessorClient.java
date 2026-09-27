package com.anssa8bu.ssafy_gpu.preprocessing.client;

import static com.anssa8bu.ssafy_gpu.preprocessing.dto.UmiPreprocessorModels.*;

import com.anssa8bu.ssafy_gpu.preprocessing.config.UmiPreprocessorProperties;
import java.net.http.HttpClient;
import java.util.Optional;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class UmiPreprocessorClient {
  private final RestClient client;

  public UmiPreprocessorClient(UmiPreprocessorProperties properties) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    factory.setReadTimeout(properties.readTimeout());
    this.client =
        RestClient.builder()
            .baseUrl(properties.baseUrl().toString())
            .requestFactory(factory)
            .build();
  }

  public void start(StartRequest request) {
    client
        .post()
        .uri("/internal/v1/jobs")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .toBodilessEntity();
  }

  public Optional<JobStatus> get(String jobId) {
    try {
      return Optional.ofNullable(
          client.get().uri("/internal/v1/jobs/{id}", jobId).retrieve().body(JobStatus.class));
    } catch (HttpClientErrorException.NotFound ignored) {
      return Optional.empty();
    }
  }

  public void cancel(String jobId) {
    // The endpoint is idempotent. Transport/server failures must be retried by the worker.
    client.post().uri("/internal/v1/jobs/{id}/cancel", jobId).retrieve().toBodilessEntity();
  }
}
