package com.anssa8bu.ssafy.scheduler;

import com.anssa8bu.ssafy.training.application.lease.TrainingLeaseRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrainingLeaseRecoveryScheduler {
  private final TrainingLeaseRecoveryService service;
  public TrainingLeaseRecoveryScheduler(TrainingLeaseRecoveryService service){this.service=service;}

  @Scheduled(fixedDelayString="${app.training-allocation.recovery-interval:30s}")
  public void recover(){service.recoverBatch();}
}
