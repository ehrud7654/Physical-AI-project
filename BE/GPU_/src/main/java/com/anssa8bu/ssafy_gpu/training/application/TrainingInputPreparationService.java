package com.anssa8bu.ssafy_gpu.training.application;

import com.anssa8bu.ssafy_gpu.fastapi.application.FastApiRunService;
import com.anssa8bu.ssafy_gpu.preprocessing.application.EpisodePreprocessingService;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
import com.anssa8bu.ssafy_gpu.training.storage.BaseModelDownloader;
import com.anssa8bu.ssafy_gpu.training.storage.EpisodeArchiveDownloader;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState.ActiveTraining;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TrainingInputPreparationService {
  private static final Logger log = LoggerFactory.getLogger(TrainingInputPreparationService.class);
  private final EpisodeArchiveDownloader downloader;
  private final BaseModelDownloader baseModels;
  private final EpisodePreprocessingService preprocessor;
  private final FastApiRunService fastApi;
  private final TrainingEventService events;
  private final WorkerRuntimeState state;
  private final ExecutorService executor;
  private final ConcurrentHashMap<String, Preparation> preparations = new ConcurrentHashMap<>();

  private static String key(UUID jobId, int attempt) {
    return jobId + ":" + attempt;
  }

  private static final class Preparation {
    private Thread thread;
    private boolean launching;
    private boolean finished;
    private volatile boolean canceled;

    synchronized void attach() {
      thread = Thread.currentThread();
      check();
    }

    synchronized void cancel() {
      canceled = true;
      // Never interrupt an in-flight create request: it may already have launched a process.
      if (thread != null && !launching && !finished) thread.interrupt();
    }

    synchronized void launching() {
      check();
      launching = true;
    }

    synchronized void finish() {
      finished = true;
      thread = null;
    }

    synchronized boolean finished() {
      return finished;
    }

    synchronized boolean mayHaveRun() {
      return launching;
    }

    void check() {
      if (canceled || Thread.currentThread().isInterrupted())
        throw new CancellationException("학습 취소 요청을 받았습니다.");
    }
  }

  public TrainingInputPreparationService(
      EpisodeArchiveDownloader downloader,
      BaseModelDownloader baseModels,
      EpisodePreprocessingService preprocessor,
      FastApiRunService fastApi,
      TrainingEventService events,
      WorkerRuntimeState state,
      @Qualifier("trainingPreparationExecutor") ExecutorService executor) {
    this.downloader = downloader;
    this.baseModels = baseModels;
    this.preprocessor = preprocessor;
    this.fastApi = fastApi;
    this.events = events;
    this.state = state;
    this.executor = executor;
  }

  public synchronized void prepare(ClaimResponse claim, int gpuIndex) {
    if (state.activeTrainingAssignments().stream()
        .noneMatch(
            value ->
                value.trainingJobId().equals(claim.job().id())
                    && value.attempt() == claim.attempt())) return;
    var preparation = new Preparation();
    preparations.put(key(claim.job().id(), claim.attempt()), preparation);
    executor.submit(() -> prepareNow(claim, gpuIndex, preparation));
  }

  public synchronized void cancel(ActiveTraining assignment) {
    var current =
        state.activeTrainingAssignments().stream()
            .filter(
                value ->
                    value.trainingJobId().equals(assignment.trainingJobId())
                        && value.attempt() == assignment.attempt())
            .findFirst();
    if (current.isEmpty()) {
      preparations.remove(key(assignment.trainingJobId(), assignment.attempt()));
      return;
    }
    var preparation = preparations.get(key(assignment.trainingJobId(), assignment.attempt()));
    if (preparation != null) {
      preparation.cancel();
      if (!preparation.finished()) return;
    }
    finishCancellation(current.get(), preparation == null || preparation.mayHaveRun());
    preparations.remove(key(assignment.trainingJobId(), assignment.attempt()));
  }

  private void finishCancellation(ActiveTraining assignment, boolean mayHaveRun) {
    if (assignment.runId() != null) {
      cancelRun(assignment, assignment.runId());
      return;
    }
    preprocessor.cancel(assignment.trainingJobId(), assignment.attempt());
    // Also recover a run whose create response was lost or whose Spring process restarted.
    String runId = key(assignment.trainingJobId(), assignment.attempt());
    if (mayHaveRun) {
      try {
        fastApi.get(runId);
        state.bindRun(assignment.gpuIndex(), runId);
        cancelRun(assignment, runId);
        return;
      } catch (org.springframework.web.client.HttpClientErrorException.NotFound absent) {
        // No training process was launched.
      }
    }
    reportCanceled(
        assignment, "preparation-canceled-" + runId, assignment.lastForwardedSequence() + 1);
  }

  private void cancelRun(ActiveTraining assignment, String runId) {
    fastApi.cancel(runId);
    var run = fastApi.get(runId);
    if (!assignment.leaseExpiresAt().isAfter(Instant.now())
        && Set.of("COMPLETED", "FAILED", "LOST", "CANCELED").contains(run.status())) {
      state.release(assignment.gpuIndex(), assignment.trainingJobId(), assignment.attempt());
      return;
    }
    // A process can finish just before the cancellation arrives; EC2 still owns the intent.
    if (Set.of("COMPLETED", "FAILED", "LOST").contains(run.status())) {
      reportCanceled(
          assignment,
          "run-canceled-" + runId,
          Math.max(run.sequence(), assignment.lastForwardedSequence()) + 2);
    }
    // CANCELED is forwarded by the run monitor using its normal event sequence.
  }

  private void reportCanceled(ActiveTraining assignment, String eventId, long sequence) {
    if (assignment.leaseExpiresAt().isAfter(Instant.now())) {
      events.canceled(
          assignment.trainingJobId(),
          assignment.attempt(),
          assignment.leaseToken(),
          eventId,
          sequence,
          Instant.now());
    }
    // If the lease expired, EC2's lease recovery owns the terminal state.
    state.release(assignment.gpuIndex(), assignment.trainingJobId(), assignment.attempt());
  }

  private void prepareNow(ClaimResponse claim, int gpuIndex, Preparation preparation) {
    try {
      preparation.attach();
      var episodes = downloader.downloadAll(claim);
      preparation.check();
      var trainingInput = preprocessor.preprocess(claim, episodes);
      preparation.check();
      var baseModel = baseModels.download(claim);
      preparation.launching();
      var run = fastApi.start(claim, gpuIndex, episodes, trainingInput, baseModel);
      preparation.check();
      events.started(
          claim.job().id(),
          claim.attempt(),
          claim.leaseToken(),
          "run-started-" + claim.job().id() + "-" + claim.attempt(),
          1,
          Instant.now());
      state.forwarded(gpuIndex, 1);
      log.info(
          "학습 입력 준비 및 FastAPI 실행 완료: trainingJobId={}, attempt={}, runId={}, episodeCount={}, trainingMode={}, baseModel={}, gpuIndex={}",
          claim.job().id(),
          claim.attempt(),
          run.runId(),
          episodes.size(),
          claim.job().trainingMode(),
          baseModel == null ? null : baseModel.storageKey(),
          gpuIndex);
    } catch (RuntimeException failure) {
      Thread.interrupted(); // Clear cancellation before HTTP cleanup/reporting.
      if (preparation.canceled || failure instanceof CancellationException) {
        preparation.canceled = true;
        log.info("학습 입력 준비 취소: trainingJobId={}, attempt={}", claim.job().id(), claim.attempt());
        return;
      }
      log.error(
          "학습 입력 준비 실패: trainingJobId={}, attempt={}, gpuIndex={}",
          claim.job().id(),
          claim.attempt(),
          gpuIndex,
          failure);
      try {
        try {
          var assignment = state.require(claim.job().id(), claim.attempt());
          if (assignment.runId() != null) fastApi.cancel(assignment.runId());
        } catch (RuntimeException cancelFailure) {
          log.warn("입력 준비 실패 후 FastAPI 취소 실패: trainingJobId={}", claim.job().id(), cancelFailure);
        }
        events.failed(
            claim.job().id(),
            claim.attempt(),
            claim.leaseToken(),
            UUID.randomUUID().toString(),
            1,
            "TRAINING_INPUT_PREPARATION_FAILED",
            failure.getMessage() == null ? "학습 입력 다운로드, 검증 또는 전처리에 실패했습니다." : failure.getMessage(),
            Instant.now());
      } catch (RuntimeException reportFailure) {
        log.error("학습 입력 준비 실패 보고 오류: trainingJobId={}", claim.job().id(), reportFailure);
      } finally {
        state.release(gpuIndex);
      }
    } finally {
      preparation.finish();
      if (preparation.canceled) {
        try {
          cancel(state.require(claim.job().id(), claim.attempt()));
        } catch (RuntimeException cancelFailure) {
          // Keep the assignment and retry on the next cancellation heartbeat.
          log.warn("학습 취소 처리 재시도 대기: trainingJobId={}", claim.job().id(), cancelFailure);
        }
      } else {
        preparations.remove(key(claim.job().id(), claim.attempt()), preparation);
      }
    }
  }
}
