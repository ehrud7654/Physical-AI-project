package com.anssa8bu.ssafy_gpu.training.recovery;

import com.anssa8bu.ssafy_gpu.fastapi.application.*;
import com.anssa8bu.ssafy_gpu.training.application.TrainingHeartbeatService;
import com.anssa8bu.ssafy_gpu.training.application.TrainingInputPreparationService;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TrainingRestartRecoveryService {
  private static final Logger log = LoggerFactory.getLogger(TrainingRestartRecoveryService.class);
  private final WorkerRuntimeState state;
  private final TrainingHeartbeatService heartbeats;
  private final FastApiRunService fastApi;
  private final TrainingCompletionOrchestrator completion;
  private final TrainingInputPreparationService preparation;
  private final AtomicBoolean recovered = new AtomicBoolean();

  public TrainingRestartRecoveryService(
      WorkerRuntimeState state,
      TrainingHeartbeatService heartbeats,
      FastApiRunService fastApi,
      TrainingCompletionOrchestrator completion,
      TrainingInputPreparationService preparation) {
    this.state = state;
    this.heartbeats = heartbeats;
    this.fastApi = fastApi;
    this.completion = completion;
    this.preparation = preparation;
  }

  @Scheduled(
      initialDelayString = "${app.worker.recovery-initial-delay:10s}",
      fixedDelayString = "${app.worker.recovery-retry-interval:10s}")
  public void recover() {
    if (recovered.get() || state.storageNodeId() == null) return;
    boolean allReconciled = true;
    for (var assignment : state.activeTrainingAssignments()) {
      try {
        var heartbeat =
            heartbeats.heartbeat(
                assignment.trainingJobId(), assignment.attempt(), assignment.leaseToken());
        state.renew(assignment.gpuIndex(), heartbeat.leaseExpiresAt());
        if (heartbeat.cancelRequested()) {
          preparation.cancel(assignment);
          continue;
        }
        if (assignment.runId() == null) {
          log.warn("runId 없는 복구 작업: trainingJobId={}", assignment.trainingJobId());
          allReconciled = false;
          continue;
        }
        var run = fastApi.get(assignment.runId());
        if ("COMPLETED".equals(run.status())) completion.completeRecovered(run);
        log.info(
            "GPU 학습 재시작 복구 완료: trainingJobId={}, runId={}, status={}",
            assignment.trainingJobId(),
            assignment.runId(),
            run.status());
      } catch (RuntimeException exception) {
        allReconciled = false;
        if (assignment.leaseExpiresAt() != null
            && !assignment.leaseExpiresAt().isAfter(Instant.now())) {
          try {
            preparation.cancel(assignment);
          } catch (RuntimeException cancelFailure) {
            exception.addSuppressed(cancelFailure);
          }
        }
        log.warn(
            "GPU 학습 재시작 복구 실패: trainingJobId={}, runId={}",
            assignment.trainingJobId(),
            assignment.runId(),
            exception);
      }
    }
    if (allReconciled) recovered.set(true);
  }
}
