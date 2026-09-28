package com.anssa8bu.ssafy_gpu.storage.client;

import static com.anssa8bu.ssafy_gpu.storage.dto.StorageNodeModels.*;

import com.anssa8bu.ssafy_gpu.common.http.Ec2ClientSupport;
import com.anssa8bu.ssafy_gpu.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class StorageNodeClient extends Ec2ClientSupport {
  private final RestClient client;

  public StorageNodeClient(@Qualifier("ec2RestClient") RestClient client) {
    this.client = client;
  }

  public NodeResponse register(RegisterRequest request) {
    return call(() ->
            client
                .post()
                .uri("/internal/storage-nodes/register")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<NodeResponse>>() {}))
        .data();
  }

  public HeartbeatResponse heartbeat(UUID id, HeartbeatRequest request) {
    return call(() ->
            client
                .post()
                .uri("/internal/storage-nodes/{id}/heartbeat", id)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<HeartbeatResponse>>() {}))
        .data();
  }
}
