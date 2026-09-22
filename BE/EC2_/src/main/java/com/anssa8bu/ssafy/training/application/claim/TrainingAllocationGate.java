package com.anssa8bu.ssafy.training.application.claim;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.ClaimRequest;

import com.anssa8bu.ssafy.common.config.TrainingAllocationProperties;
import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.storage.entity.*;
import com.anssa8bu.ssafy.storage.repository.StorageNodeRepository;
import com.anssa8bu.ssafy.training.entity.TrainingJobStatus;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TrainingAllocationGate {
  public static final Set<TrainingJobStatus> ACTIVE =
      Collections.unmodifiableSet(
          EnumSet.of(
              TrainingJobStatus.COLLECTING,
              TrainingJobStatus.PREPARING,
              TrainingJobStatus.RUNNING,
              TrainingJobStatus.MODEL_UPLOADING,
              TrainingJobStatus.MODEL_VALIDATING,
              TrainingJobStatus.CANCEL_REQUESTED));
  private final RuntimeStateService runtime;
  private final StorageNodeRepository nodes;
  private final TrainingJobRepository jobs;
  private final TrainingAllocationProperties policy;
  private final Clock clock;

  public TrainingAllocationGate(
      StorageNodeRepository nodes,
      TrainingJobRepository jobs,
      TrainingAllocationProperties policy,
      Clock clock,
      RuntimeStateService runtime) {
    this.runtime = runtime;
    this.nodes = nodes;
    this.jobs = jobs;
    this.policy = policy;
    this.clock = clock;
  }

  public StorageNode validate(ClaimRequest request, boolean hasExistingAssignment) {
    if (request.gpuIndex() >= policy.maxActiveJobsPerWorker())
      reject("GPU_INDEX_OUT_OF_RANGE", "gpuIndex는 0 이상 최대 병렬 학습 수보다 작아야 합니다.");
    StorageNode node =
        nodes
            .findByIdForUpdate(request.storageNodeId())
            .orElseThrow(() -> new ResourceNotFoundException("StorageNode"));
    if (!node.getWorkerId().equals(request.workerId()))
      reject("STORAGE_NODE_WORKER_MISMATCH", "StorageNode의 workerId가 일치하지 않습니다.");
    if (node.getStorageType() != StorageType.LOCAL)
      reject("STORAGE_NODE_TYPE_MISMATCH", "학습 Worker는 LOCAL StorageNode를 사용해야 합니다.");
    var live =
        runtime
            .storage(node.getId())
            .orElseThrow(
                () ->
                    new BusinessConflictException(
                        "STORAGE_NODE_STALE", "StorageNode heartbeat가 만료되었습니다."));
    if (!live.workerId().equals(request.workerId()))
      reject("STORAGE_NODE_WORKER_MISMATCH", "StorageNode의 workerId가 일치하지 않습니다.");
    if (live.status() != StorageNodeStatus.ONLINE)
      reject("STORAGE_NODE_UNAVAILABLE", "StorageNode가 ONLINE 상태가 아닙니다.");
    if (!live.lastSeenAt().plus(policy.storageNodeOfflineAfter()).isAfter(clock.instant()))
      reject("STORAGE_NODE_STALE", "StorageNode heartbeat가 만료되었습니다.");
    long available =
        Math.min(
            live.availableBytes(),
            Optional.ofNullable(request.storageAvailableBytes()).orElse(live.availableBytes()));
    if (available < policy.minAvailableStorageBytes())
      reject("INSUFFICIENT_STORAGE", "GPU 로컬 저장 공간이 부족합니다.");
    if (Optional.ofNullable(request.availableMemoryMiB()).orElse(0L)
        < policy.minAvailableMemoryMib()) reject("INSUFFICIENT_GPU_MEMORY", "GPU 가용 메모리가 부족합니다.");
    if (!hasExistingAssignment
        && Math.max(
                jobs.countByAssignedWorkerIdAndStatusIn(request.workerId(), ACTIVE),
                live.activeTrainingCount())
            >= policy.maxActiveJobsPerWorker()) reject("WORKER_BUSY", "Worker의 활성 학습 한도를 초과했습니다.");
    if (!hasExistingAssignment
        && jobs.existsByAssignedWorkerIdAndGpuIndexAndStatusIn(
            request.workerId(), request.gpuIndex(), ACTIVE))
      reject("GPU_BUSY", "요청한 GPU가 이미 학습에 사용 중입니다.");
    return node;
  }

  private static void reject(String code, String message) {
    throw new BusinessConflictException(code, message);
  }
}
