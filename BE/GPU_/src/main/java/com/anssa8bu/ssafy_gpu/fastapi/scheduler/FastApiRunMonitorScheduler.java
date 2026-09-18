package com.anssa8bu.ssafy_gpu.fastapi.scheduler;

import com.anssa8bu.ssafy_gpu.fastapi.application.FastApiRunService;
import com.anssa8bu.ssafy_gpu.fastapi.application.TrainingCompletionOrchestrator;
import com.anssa8bu.ssafy_gpu.training.application.TrainingEventService;
import com.anssa8bu.ssafy_gpu.training.cleanup.TrainingArtifactCleanupService;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState.ActiveTraining;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FastApiRunMonitorScheduler {
  private static final Logger log = LoggerFactory.getLogger(FastApiRunMonitorScheduler.class);
  private final FastApiRunService fastApi;
  private final TrainingEventService events;
  private final WorkerRuntimeState state;
  private final TrainingArtifactCleanupService cleanup;
  private final TrainingCompletionOrchestrator completion;

  public FastApiRunMonitorScheduler(
      FastApiRunService fastApi,
      TrainingEventService events,
      WorkerRuntimeState state,
      TrainingArtifactCleanupService cleanup,
      TrainingCompletionOrchestrator completion) {
    this.fastApi = fastApi;
    this.events = events;
    this.state = state;
    this.cleanup = cleanup;
    this.completion = completion;
  }

  @Scheduled(
      initialDelayString = "${app.fastapi.monitor-interval:2s}",
      fixedDelayString = "${app.fastapi.monitor-interval:2s}")
  public void monitor() {
    for (ActiveTraining assignment : state.activeTrainingAssignments()) {
      if (assignment.runId() == null) continue;
      inspect(assignment);
    }
  }

  private void inspect(ActiveTraining assignment) {
    try {
      var run = fastApi.get(assignment.runId());
      if (run.sequence() <= assignment.lastForwardedSequence()) return;
      long eventSequence = run.sequence() + 1;
      String eventId = "fastapi-" + assignment.runId() + "-" + run.sequence();
      switch (run.status()) {
        case "STARTING" -> {
          return;
        }
        case "RUNNING", "CANCEL_REQUESTED" ->
            events.progress(
                assignment.trainingJobId(),
                assignment.attempt(),
                assignment.leaseToken(),
                eventId,
                eventSequence,
                run.stage(),
                run.percent(),
                run.epoch(),
                run.totalEpochs(),
                Map.of(),
                occurredAt(run.lastProgressAt()));
        case "CANCELED" -> {
          events.canceled(
              assignment.trainingJobId(),
              assignment.attempt(),
              assignment.leaseToken(),
              eventId,
              eventSequence,
              occurredAt(run.lastProgressAt()));
          state.release(assignment.gpuIndex());
          cleanup.cleanup(assignment.trainingJobId(), assignment.attempt());
        }
        case "FAILED", "LOST" -> {
          String code = run.error() == null ? "FASTAPI_TRAINING_FAILED" : run.error().code();
          String message = run.error() == null ? "FastAPI 학습 실행이 실패했습니다." : run.error().message();
          events.failed(
              assignment.trainingJobId(),
              assignment.attempt(),
              assignment.leaseToken(),
              eventId,
              eventSequence,
              code,
              message,
              occurredAt(run.lastProgressAt()));
          state.release(assignment.gpuIndex());
          cleanup.cleanup(assignment.trainingJobId(), assignment.attempt());
        }
        case "COMPLETED" -> {
          completion.completeRecovered(run);
        }
        default -> throw new IllegalStateException("알 수 없는 FastAPI 상태: " + run.status());
      }
      state.forwarded(assignment.gpuIndex(), run.sequence());
    } catch (RuntimeException e) {
      log.warn(
          "FastAPI 학습 상태 조회 또는 EC2 이벤트 전달 실패: trainingJobId={}, runId={}",
          assignment.trainingJobId(),
          assignment.runId(),
          e);
    }
  }

  private static Instant occurredAt(Instant value) {
    return value == null ? Instant.now() : value;
  }
}
