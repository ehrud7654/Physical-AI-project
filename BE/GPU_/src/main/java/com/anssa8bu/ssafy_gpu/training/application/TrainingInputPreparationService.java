package com.anssa8bu.ssafy_gpu.training.application;

import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
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
  private final TrainingEventService events;
  private final WorkerRuntimeState state;
  private final ExecutorService executor;

  public TrainingInputPreparationService(
      EpisodeArchiveDownloader downloader,
      TrainingEventService events,
      WorkerRuntimeState state,
      @Qualifier("trainingPreparationExecutor") ExecutorService executor) {
    this.downloader = downloader;
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
      log.info(
          "학습 입력 준비 완료: trainingJobId={}, attempt={}, episodeCount={}, gpuIndex={}",
          claim.job().id(),
          claim.attempt(),
          episodes.size(),
          gpuIndex);
    } catch (RuntimeException failure) {
      log.error(
          "학습 입력 준비 실패: trainingJobId={}, attempt={}, gpuIndex={}",
          claim.job().id(),
          claim.attempt(),
          gpuIndex,
          failure);
      try {
        events.failed(
            claim.job().id(),
            claim.attempt(),
            claim.leaseToken(),
            UUID.randomUUID().toString(),
            1,
            "EPISODE_DOWNLOAD_FAILED",
            failure.getMessage() == null ? "Episode 다운로드 또는 검증에 실패했습니다." : failure.getMessage(),
            Instant.now());
      } catch (RuntimeException reportFailure) {
        log.error("학습 입력 준비 실패 보고 오류: trainingJobId={}", claim.job().id(), reportFailure);
      } finally {
        state.release(gpuIndex);
      }
    }
  }
}
