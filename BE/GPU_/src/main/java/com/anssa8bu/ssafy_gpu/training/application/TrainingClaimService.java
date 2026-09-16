package com.anssa8bu.ssafy_gpu.training.application;

import static com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.storage.application.LocalStorageMetrics;
import com.anssa8bu.ssafy_gpu.training.client.TrainingJobClient;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TrainingClaimService {
  private final TrainingJobClient client;
  private final WorkerProperties properties;
  private final WorkerRuntimeState state;
  private final LocalStorageMetrics storage;

  public TrainingClaimService(
      TrainingJobClient client,
      WorkerProperties properties,
      WorkerRuntimeState state,
      LocalStorageMetrics storage) {
    this.client = client;
    this.properties = properties;
    this.state = state;
    this.storage = storage;
  }

  public Optional<ClaimResponse> claim(int gpuIndex, long availableMemoryMiB, String gitRevision) {
    if (gpuIndex < 0 || gpuIndex >= properties.maxGpuSlots())
      throw new IllegalArgumentException("gpuIndex 범위가 올바르지 않습니다.");
    if (state.storageNodeId() == null) throw new IllegalStateException("StorageNode가 등록되지 않았습니다.");
    var capacity = storage.capacity();
    return client.claim(
        new ClaimRequest(
            properties.workerId(),
            gpuIndex,
            gitRevision,
            state.storageNodeId(),
            properties.supportedAlgorithms(),
            availableMemoryMiB,
            capacity.totalBytes(),
            capacity.availableBytes()));
  }
}
