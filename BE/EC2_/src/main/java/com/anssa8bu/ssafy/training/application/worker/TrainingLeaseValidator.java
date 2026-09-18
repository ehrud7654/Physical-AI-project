package com.anssa8bu.ssafy.training.application.worker;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.lease.LeaseTokenService;
import com.anssa8bu.ssafy.training.entity.TrainingJob;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TrainingLeaseValidator {
  private final LeaseTokenService tokens;
  public TrainingLeaseValidator(LeaseTokenService tokens) { this.tokens = tokens; }
  public void validate(TrainingJob job, String workerId, int attempt, String token, Instant now) {
    boolean invalid = job.getAttempt() != attempt
        || workerId != null && !workerId.equals(job.getAssignedWorkerId())
        || job.getLeaseExpiresAt() == null || !job.getLeaseExpiresAt().isAfter(now)
        || job.getLeaseTokenHash() == null || !job.getLeaseTokenHash().equals(tokens.hash(token));
    if (invalid) throw new BusinessConflictException("LEASE_EXPIRED", "유효하지 않거나 만료된 학습 lease입니다.");
  }
}
