package com.anssa8bu.ssafy_gpu.checkpoint.application;

import static com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.*;

import com.anssa8bu.ssafy_gpu.checkpoint.client.CheckpointClient;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckpointCompleteService {
  private final CheckpointClient client;

  public CheckpointCompleteService(CheckpointClient client) {
    this.client = client;
  }

  public CompleteResponse complete(UUID jobId, String idempotencyKey, CompleteRequest request) {
    return client.complete(jobId, idempotencyKey, request);
  }
}
