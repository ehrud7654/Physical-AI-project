package com.anssa8bu.ssafy_gpu.storage.application;

import static com.anssa8bu.ssafy_gpu.storage.dto.StorageNodeModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.storage.client.StorageNodeClient;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import org.springframework.stereotype.Service;

@Service
public class StorageNodeRegistrationService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StorageNodeRegistrationService.class);
  private final StorageNodeClient client;
  private final WorkerProperties properties;
  private final LocalStorageMetrics metrics;
  private final WorkerRuntimeState state;

  public StorageNodeRegistrationService(
      StorageNodeClient client,
      WorkerProperties properties,
      LocalStorageMetrics metrics,
      WorkerRuntimeState state) {
    this.client = client;
    this.properties = properties;
    this.metrics = metrics;
    this.state = state;
  }

  public NodeResponse register() {
    var capacity = metrics.capacity();
    NodeResponse response =
        client.register(
            new RegisterRequest(
                properties.workerId(),
                properties.nodeKey(),
                properties.nodeName(),
                StorageType.LOCAL,
                capacity.totalBytes(),
                capacity.availableBytes()));
    state.storageNodeId(response.id());
    log.info("StorageNode 등록 완료: workerId={}, storageNodeId={}", properties.workerId(), response.id());
    return response;
  }
}
