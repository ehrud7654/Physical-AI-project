package com.anssa8bu.ssafy_gpu.storage.application;

import static com.anssa8bu.ssafy_gpu.storage.dto.StorageNodeModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.storage.client.StorageNodeClient;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StorageNodeHeartbeatService {
  private final StorageNodeClient client;
  private final WorkerProperties properties;
  private final LocalStorageMetrics metrics;
  private final WorkerRuntimeState state;

  public StorageNodeHeartbeatService(
      StorageNodeClient client,
      WorkerProperties properties,
      LocalStorageMetrics metrics,
      WorkerRuntimeState state) {
    this.client = client;
    this.properties = properties;
    this.metrics = metrics;
    this.state = state;
  }

  public HeartbeatResponse heartbeat() {
    UUID id = state.storageNodeId();
    if (id == null) throw new IllegalStateException("StorageNode가 등록되지 않았습니다.");
    var capacity = metrics.capacity();
    return client.heartbeat(
        id,
        new HeartbeatRequest(
            properties.workerId(),
            StorageNodeStatus.ONLINE,
            capacity.totalBytes(),
            capacity.availableBytes(),
            state.activeUploads(),
            state.activeTrainings()));
  }
}
