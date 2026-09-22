package com.anssa8bu.ssafy_gpu.training.application;

import com.anssa8bu.ssafy_gpu.fastapi.application.FastApiRunService;
import com.anssa8bu.ssafy_gpu.preprocessing.application.EpisodePreprocessingService;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
import com.anssa8bu.ssafy_gpu.training.storage.BaseModelDownloader;
import com.anssa8bu.ssafy_gpu.training.storage.EpisodeArchiveDownloader;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.time.Instant;
import java.util.*;
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

  public void prepare(ClaimResponse claim, int gpuIndex) {
    executor.submit(() -> prepareNow(claim, gpuIndex));
  }

  private void prepareNow(ClaimResponse claim, int gpuIndex) {
    try {
      var episodes = downloader.downloadAll(claim);
      var trainingInput = preprocessor.preprocess(claim, episodes);
      var baseModel = baseModels.download(claim);
      var run = fastApi.start(claim, gpuIndex, episodes, trainingInput, baseModel);
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
    }
  }
}
