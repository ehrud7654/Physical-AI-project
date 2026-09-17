package com.anssa8bu.ssafy_gpu.checkpoint.application;

import static com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.*;

import com.anssa8bu.ssafy_gpu.checkpoint.client.CheckpointClient;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckpointUploadSessionService {
  private final CheckpointClient client;

  public CheckpointUploadSessionService(CheckpointClient client) {
    this.client = client;
  }

  public UploadSessionResponse issue(
      UUID jobId, String idempotencyKey, UploadSessionRequest request) {
    return client.createUploadSession(jobId, idempotencyKey, request);
  }
}
