package com.anssa8bu.ssafy.storage.application;

import com.anssa8bu.ssafy.common.config.TrainingAllocationProperties;
import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.storage.dto.internal.StorageNodeApiModels.HeartbeatRequest;
import com.anssa8bu.ssafy.storage.dto.internal.StorageNodeApiModels.HeartbeatResponse;
import com.anssa8bu.ssafy.storage.dto.internal.StorageNodeApiModels.NodeResponse;
import com.anssa8bu.ssafy.storage.dto.internal.StorageNodeApiModels.RegisterRequest;
import com.anssa8bu.ssafy.storage.entity.StorageNode;
import com.anssa8bu.ssafy.storage.entity.StorageNodeStatus;
import com.anssa8bu.ssafy.storage.entity.StorageType;
import com.anssa8bu.ssafy.storage.repository.StorageNodeRepository;
import com.anssa8bu.ssafy.training.application.claim.TrainingAllocationGate;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageNodeService {

  private final StorageNodeRepository storageNodeRepository;
  private final TrainingJobRepository trainingJobRepository;
  private final TrainingAllocationProperties allocationProperties;
  private final Clock clock;
  private final int heartbeatIntervalSeconds;

  public StorageNodeService(
      StorageNodeRepository storageNodeRepository,
      TrainingJobRepository trainingJobRepository,
      TrainingAllocationProperties allocationProperties,
      Clock clock,
      @Value("${app.training-worker.heartbeat-interval-seconds}") int heartbeatIntervalSeconds) {
    this.storageNodeRepository = storageNodeRepository;
    this.trainingJobRepository = trainingJobRepository;
    this.allocationProperties = allocationProperties;
    this.clock = clock;
    this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
  }

  @Transactional
  public RegistrationResult register(RegisterRequest request) {
    validateCapacity(request.capacityBytes(), request.availableBytes());
    if (request.storageType() != StorageType.LOCAL) {
      throw new BusinessConflictException(
          "STORAGE_NODE_TYPE_MISMATCH", "GPU Worker 저장소는 LOCAL 타입이어야 합니다.");
    }

    var existing = storageNodeRepository.findByWorkerIdAndNodeKey(
        request.workerId(), request.nodeKey());
    boolean created = existing.isEmpty();
    StorageNode node = existing.orElseGet(() -> new StorageNode(
        UUID.randomUUID(), request.workerId(), request.nodeKey(), request.name(),
        request.storageType(), StorageNodeStatus.ONLINE,
        request.capacityBytes(), request.availableBytes()));

    if (!created) {
      node.rename(request.name());
    }
    node.reportHeartbeat(
        StorageNodeStatus.ONLINE,
        request.capacityBytes(),
        request.availableBytes(),
        clock.instant());
    storageNodeRepository.save(node);
    return new RegistrationResult(toResponse(node), created);
  }

  @Transactional
  public HeartbeatResponse heartbeat(UUID storageNodeId, HeartbeatRequest request) {
    StorageNode node = storageNodeRepository.findByIdForUpdate(storageNodeId)
        .orElseThrow(() -> new ResourceNotFoundException("StorageNode"));
    if (!node.getWorkerId().equals(request.workerId())) {
      throw new BusinessConflictException(
          "STORAGE_NODE_WORKER_MISMATCH", "StorageNode의 workerId가 일치하지 않습니다.");
    }
    validateCapacity(request.capacityBytes(), request.availableBytes());

    Instant now = clock.instant();
    node.reportHeartbeat(
        request.status(), request.capacityBytes(), request.availableBytes(), now);

    long databaseActiveCount = trainingJobRepository.countByAssignedWorkerIdAndStatusIn(
        request.workerId(), TrainingAllocationGate.ACTIVE);
    long effectiveActiveCount = Math.max(databaseActiveCount, request.activeTrainingCount());
    boolean acceptsNewUploads = request.status() == StorageNodeStatus.ONLINE
        && request.availableBytes() >= allocationProperties.minAvailableStorageBytes()
        && effectiveActiveCount < allocationProperties.maxActiveJobsPerWorker();

    return new HeartbeatResponse(now, heartbeatIntervalSeconds, acceptsNewUploads);
  }

  private void validateCapacity(long capacityBytes, long availableBytes) {
    if (availableBytes > capacityBytes) {
      throw new BusinessConflictException(
          "INVALID_STORAGE_CAPACITY", "가용 용량은 전체 용량을 초과할 수 없습니다.");
    }
  }

  private NodeResponse toResponse(StorageNode node) {
    return new NodeResponse(
        node.getId(), node.getWorkerId(), node.getNodeKey(), node.getName(),
        node.getStorageType(), node.getStatus(), node.getCapacityBytes(),
        node.getAvailableBytes(), node.getLastSeenAt());
  }

  public record RegistrationResult(NodeResponse node, boolean created) {}
}
