package com.anssa8bu.ssafy_gpu.training.application;

import static com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.storage.application.LocalStorageMetrics;
import com.anssa8bu.ssafy_gpu.training.client.TrainingJobClient;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TrainingHeartbeatService {
  private final TrainingJobClient client;
  private final WorkerProperties properties;
  private final WorkerRuntimeState state;
  private final LocalStorageMetrics storage;

  public TrainingHeartbeatService(
      TrainingJobClient client,
      WorkerProperties properties,
      WorkerRuntimeState state,
      LocalStorageMetrics storage) {
    this.client = client;
    this.properties = properties;
    this.state = state;
    this.storage = storage;
  }

  public HeartbeatResponse heartbeat(UUID jobId, int attempt, String leaseToken) {
    if (state.storageNodeId() == null) throw new IllegalStateException("StorageNode가 등록되지 않았습니다.");
    return client.heartbeat(
        jobId,
        new HeartbeatRequest(
            properties.workerId(),
            state.storageNodeId(),
            attempt,
            leaseToken,
            storage.capacity().availableBytes()));
  }
}
