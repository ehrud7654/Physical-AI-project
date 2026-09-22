package com.anssa8bu.ssafy.training.application.worker;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.*;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.common.redis.RedisTtlProperties;
import com.anssa8bu.ssafy.common.redis.dto.TrainingLeaseCache;
import com.anssa8bu.ssafy.common.redis.repository.TrainingLeaseCacheRepository;
import com.anssa8bu.ssafy.storage.repository.StorageNodeRepository;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingHeartbeatService {
  private final TrainingJobRepository jobs;
  private final TrainingLeaseValidator leases;
  private final Clock clock;
  private final Duration duration;
  private final StorageNodeRepository nodes;
  private final TrainingLeaseCacheRepository cache;
  private final RedisTtlProperties redisTtl;

  public TrainingHeartbeatService(
      TrainingJobRepository jobs,
      TrainingLeaseValidator leases,
      Clock clock,
      @Value("${app.training-worker.lease-duration}") Duration duration,
      StorageNodeRepository nodes,
      TrainingLeaseCacheRepository cache,
      RedisTtlProperties redisTtl) {
    this.jobs = jobs;
    this.leases = leases;
    this.clock = clock;
    this.duration = duration;
    this.nodes = nodes;
    this.cache = cache;
    this.redisTtl = redisTtl;
  }

  @Transactional
  public HeartbeatResponse heartbeat(UUID id, HeartbeatRequest request) {
    TrainingJob job =
        jobs.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("TrainingJob"));
    Instant now = clock.instant();
    leases.validate(job, request.workerId(), request.attempt(), request.leaseToken(), now);
    if (request.storageNodeId() == null
        || !request.storageNodeId().equals(job.getAssignedStorageNodeId()))
      throw new BusinessConflictException("STORAGE_NODE_MISMATCH", "할당된 StorageNode가 아닙니다.");
    var node =
        nodes
            .findById(request.storageNodeId())
            .orElseThrow(() -> new ResourceNotFoundException("StorageNode"));
    if (!node.getWorkerId().equals(request.workerId()))
      throw new BusinessConflictException(
          "STORAGE_NODE_WORKER_MISMATCH", "StorageNode의 workerId가 일치하지 않습니다.");
    // Only the dedicated storage heartbeat owns live capacity/status.
    // Training lease renewal continues independently of Redis storage availability.
    Instant expires = now.plus(duration);
    job.renewLease(expires);
    try {
      cache.save(
          job.getId(),
          new TrainingLeaseCache(
              1,
              job.getId(),
              job.getAttempt(),
              job.getAssignedWorkerId(),
              job.getLeaseTokenHash(),
              expires),
          redisTtl.trainingLease());
    } catch (RuntimeException ignored) {
    }
    return new HeartbeatResponse(
        expires, job.getStatus() == TrainingJobStatus.CANCEL_REQUESTED
            || job.getStatus() == TrainingJobStatus.CANCELED, now);
  }
}
