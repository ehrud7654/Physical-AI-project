package com.anssa8bu.ssafy.training.application.worker;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.*;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingHeartbeatService {
  private final TrainingJobRepository jobs; private final TrainingLeaseValidator leases;
  private final Clock clock; private final Duration duration;
  public TrainingHeartbeatService(TrainingJobRepository jobs, TrainingLeaseValidator leases,
      Clock clock, @Value("${app.training-worker.lease-duration}") Duration duration) {
    this.jobs=jobs; this.leases=leases; this.clock=clock; this.duration=duration;
  }
  @Transactional
  public HeartbeatResponse heartbeat(UUID id, HeartbeatRequest request) {
    TrainingJob job=jobs.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("TrainingJob"));
    Instant now=clock.instant();
    leases.validate(job, request.workerId(), request.attempt(), request.leaseToken(), now);
    Instant expires=now.plus(duration); job.renewLease(expires);
    return new HeartbeatResponse(expires, job.getStatus()==TrainingJobStatus.CANCEL_REQUESTED,
        false, now);
  }
}
