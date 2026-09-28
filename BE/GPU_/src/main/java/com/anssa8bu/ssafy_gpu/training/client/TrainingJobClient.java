package com.anssa8bu.ssafy_gpu.training.client;

import static com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.*;

import com.anssa8bu.ssafy_gpu.common.http.Ec2ClientSupport;
import com.anssa8bu.ssafy_gpu.common.response.ApiResponse;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TrainingJobClient extends Ec2ClientSupport {
  private final RestClient client;

  public TrainingJobClient(@Qualifier("ec2RestClient") RestClient client) {
    this.client = client;
  }

  public Optional<ClaimResponse> claim(ClaimRequest request) {
    ResponseEntity<ApiResponse<ClaimResponse>> response =
        call(
            () ->
                client
                    .post()
                    .uri("/internal/training-jobs/claim")
                    .body(request)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<ApiResponse<ClaimResponse>>() {}));
    return Optional.ofNullable(response.getBody()).map(ApiResponse::data);
  }

  public HeartbeatResponse heartbeat(UUID id, HeartbeatRequest request) {
    return call(() ->
            client
                .post()
                .uri("/internal/training-jobs/{id}/heartbeat", id)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<HeartbeatResponse>>() {}))
        .data();
  }

  public EventResponse event(UUID id, EventRequest request) {
    return call(() ->
            client
                .post()
                .uri("/internal/training-jobs/{id}/events", id)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<EventResponse>>() {}))
        .data();
  }
}
