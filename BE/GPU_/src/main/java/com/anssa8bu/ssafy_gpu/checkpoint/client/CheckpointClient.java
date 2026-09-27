package com.anssa8bu.ssafy_gpu.checkpoint.client;

import static com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.*;

import com.anssa8bu.ssafy_gpu.common.http.Ec2ClientSupport;
import com.anssa8bu.ssafy_gpu.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CheckpointClient extends Ec2ClientSupport {
  private final RestClient client;

  public CheckpointClient(@Qualifier("ec2RestClient") RestClient client) {
    this.client = client;
  }

  public UploadSessionResponse createUploadSession(
      UUID jobId, String key, UploadSessionRequest request) {
    return call(() ->
            client
                .post()
                .uri("/internal/training-jobs/{id}/checkpoint-upload-session", jobId)
                .header("Idempotency-Key", key)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<UploadSessionResponse>>() {}))
        .data();
  }

  public CompleteResponse complete(UUID jobId, String key, CompleteRequest request) {
    return call(() ->
            client
                .post()
                .uri("/internal/training-jobs/{id}/checkpoint-complete", jobId)
                .header("Idempotency-Key", key)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<CompleteResponse>>() {}))
        .data();
  }
}
