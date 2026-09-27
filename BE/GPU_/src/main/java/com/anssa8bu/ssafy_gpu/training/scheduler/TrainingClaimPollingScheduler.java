package com.anssa8bu.ssafy_gpu.training.scheduler;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.fastapi.application.FastApiRunService;
import com.anssa8bu.ssafy_gpu.training.application.TrainingClaimService;
import com.anssa8bu.ssafy_gpu.training.application.TrainingHeartbeatService;
import com.anssa8bu.ssafy_gpu.training.application.TrainingInputPreparationService;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState.ActiveTraining;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrainingClaimPollingScheduler {
  private static final Logger log = LoggerFactory.getLogger(TrainingClaimPollingScheduler.class);

  private final TrainingClaimService claims;
  private final TrainingHeartbeatService heartbeats;
  private final WorkerRuntimeState state;
  private final WorkerProperties properties;
  private final TrainingInputPreparationService inputPreparation;
  private final FastApiRunService fastApi;

  public TrainingClaimPollingScheduler(
      TrainingClaimService claims,
      TrainingHeartbeatService heartbeats,
      WorkerRuntimeState state,
      WorkerProperties properties,
      TrainingInputPreparationService inputPreparation,
      FastApiRunService fastApi) {
    this.claims = claims;
    this.heartbeats = heartbeats;
    this.state = state;
    this.properties = properties;
    this.inputPreparation = inputPreparation;
    this.fastApi = fastApi;
  }

  @Scheduled(
      initialDelayString = "${app.worker.claim-polling-initial-delay:20s}",
      fixedDelayString = "${app.worker.claim-polling-interval:5s}")
  public void poll() {
    if (!properties.claimPollingEnabled() || state.storageNodeId() == null) return;

    for (int gpuIndex = 0; gpuIndex < properties.maxGpuSlots(); gpuIndex++) {
      if (state.isGpuBusy(gpuIndex)) continue;
      claim(gpuIndex);
    }
  }

  @Scheduled(
      initialDelayString = "${app.worker.storage-heartbeat-interval:15s}",
      fixedDelayString = "${app.worker.storage-heartbeat-interval:15s}")
  public void renewLeases() {
    if (!properties.claimPollingEnabled()) return;
    for (ActiveTraining assignment : state.activeTrainingAssignments()) {
      renew(assignment);
    }
  }

  private void claim(int gpuIndex) {
    try {
      claims
          .claim(gpuIndex, properties.claimAvailableMemoryMib(), properties.gitRevision())
          .ifPresent(
              response -> {
                ActiveTraining assignment =
                    new ActiveTraining(
                        gpuIndex,
                        response.job().id(),
                        response.attempt(),
                        response.leaseToken(),
                        response.leaseExpiresAt());
                if (state.activate(assignment)) {
                  log.info(
                      "학습 작업 claim 성공: trainingJobId={}, attempt={}, gpuIndex={}, leaseExpiresAt={}",
                      assignment.trainingJobId(),
                      assignment.attempt(),
                      gpuIndex,
                      assignment.leaseExpiresAt());
                  inputPreparation.prepare(response, gpuIndex);
                }
              });
    } catch (RuntimeException e) {
      log.warn("학습 작업 claim 실패: gpuIndex={}", gpuIndex, e);
    }
  }

  private void renew(ActiveTraining assignment) {
    try {
      var response =
          heartbeats.heartbeat(
              assignment.trainingJobId(), assignment.attempt(), assignment.leaseToken());
      if (response.cancelRequested()) {
        log.info(
            "학습 취소 요청 수신: trainingJobId={}, gpuIndex={}",
            assignment.trainingJobId(),
            assignment.gpuIndex());
        state.renew(assignment.gpuIndex(), response.leaseExpiresAt());
        inputPreparation.cancel(assignment);
        return;
      }
      state.renew(assignment.gpuIndex(), response.leaseExpiresAt());
      if (assignment.runId() != null && !fastApi.isTerminal(assignment.runId()))
        fastApi.renewLease(assignment.runId(), response.leaseExpiresAt());
    } catch (RuntimeException e) {
      if (assignment.leaseExpiresAt() != null
          && !assignment.leaseExpiresAt().isAfter(java.time.Instant.now())) {
        try {
          inputPreparation.cancel(assignment);
        } catch (RuntimeException cancelFailure) {
          e.addSuppressed(cancelFailure);
        }
      }
      log.warn(
          "학습 lease 갱신 실패: trainingJobId={}, gpuIndex={}, leaseExpiresAt={}",
          assignment.trainingJobId(),
          assignment.gpuIndex(),
          assignment.leaseExpiresAt(),
          e);
    }
  }
}
