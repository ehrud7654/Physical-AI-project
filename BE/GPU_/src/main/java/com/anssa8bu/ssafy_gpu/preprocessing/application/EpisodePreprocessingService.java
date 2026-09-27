package com.anssa8bu.ssafy_gpu.preprocessing.application;

import static com.anssa8bu.ssafy_gpu.preprocessing.dto.UmiPreprocessorModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.preprocessing.client.UmiPreprocessorClient;
import com.anssa8bu.ssafy_gpu.preprocessing.config.UmiPreprocessorProperties;
import com.anssa8bu.ssafy_gpu.preprocessing.storage.*;
import com.anssa8bu.ssafy_gpu.preprocessing.storage.PreprocessedArtifactVerifier.PreprocessedTrainingInput;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
import com.anssa8bu.ssafy_gpu.training.storage.EpisodeArchiveDownloader.LocalEpisode;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class EpisodePreprocessingService {
  private static final Logger log = LoggerFactory.getLogger(EpisodePreprocessingService.class);
  private final WorkerProperties worker;
  private final UmiPreprocessorProperties properties;
  private final UmiPreprocessorClient client;
  private final SafeEpisodeArchiveExtractor extractor;
  private final PreprocessedArtifactVerifier verifier;
  private final Semaphore singleWorker = new Semaphore(1);

  public EpisodePreprocessingService(
      WorkerProperties worker,
      UmiPreprocessorProperties properties,
      UmiPreprocessorClient client,
      SafeEpisodeArchiveExtractor extractor,
      PreprocessedArtifactVerifier verifier) {
    this.worker = worker;
    this.properties = properties;
    this.client = client;
    this.extractor = extractor;
    this.verifier = verifier;
  }

  public PreprocessedTrainingInput preprocess(ClaimResponse claim, List<LocalEpisode> episodes) {
    Instant startedAt = Instant.now();
    log.info(
        "Episode 전처리 단계 진입: trainingJobId={}, attempt={}, episodeCount={}",
        claim.job().id(),
        claim.attempt(),
        episodes.size());
    try {
      PreprocessedTrainingInput result = preprocessInternal(claim, episodes, startedAt);
      log.info(
          "Episode 전처리 단계 완료: trainingJobId={}, attempt={}, acceptedEpisodeCount={}, format={}, worldFrame={}, elapsedMs={}",
          claim.job().id(),
          claim.attempt(),
          result.episodeCount(),
          result.format(),
          result.worldFrame(),
          Duration.between(startedAt, Instant.now()).toMillis());
      return result;
    } catch (java.util.concurrent.CancellationException exception) {
      log.warn(
          "Episode 전처리 단계 취소: trainingJobId={}, attempt={}, elapsedMs={}, reason={}",
          claim.job().id(),
          claim.attempt(),
          Duration.between(startedAt, Instant.now()).toMillis(),
          exception.getMessage());
      throw exception;
    } catch (RuntimeException exception) {
      log.error(
          "Episode 전처리 단계 오류: trainingJobId={}, attempt={}, elapsedMs={}, errorType={}, message={}",
          claim.job().id(),
          claim.attempt(),
          Duration.between(startedAt, Instant.now()).toMillis(),
          exception.getClass().getSimpleName(),
          exception.getMessage(),
          exception);
      throw exception;
    }
  }

  private PreprocessedTrainingInput preprocessInternal(
      ClaimResponse claim, List<LocalEpisode> episodes, Instant startedAt) {
    String jobId = claim.job().id() + "_" + claim.attempt();
    Path root =
        worker
            .storageRoot()
            .resolve("preprocessing-jobs")
            .resolve(jobId)
            .toAbsolutePath()
            .normalize();
    Path allowed = worker.storageRoot().resolve("preprocessing-jobs").toAbsolutePath().normalize();
    if (!root.startsWith(allowed)) throw new IllegalStateException("허용되지 않은 전처리 작업 경로입니다.");
    if (episodes.isEmpty()) throw new IllegalStateException("전처리에는 하나 이상의 Episode가 필요합니다.");
    List<LocalEpisode> mappings =
        episodes.stream()
            .filter(
                episode ->
                    episode.inputRole()
                        == com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.TrainingEpisodeRole
                            .MAPPING)
            .toList();
    List<LocalEpisode> demonstrations =
        episodes.stream()
            .filter(
                episode ->
                    episode.inputRole()
                        == com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.TrainingEpisodeRole
                            .DEMONSTRATION)
            .toList();
    if (mappings.size() != 1)
      throw new IllegalStateException("전처리에는 Mapping Episode가 정확히 하나 필요합니다.");
    if (demonstrations.isEmpty())
      throw new IllegalStateException("전처리에는 Demonstration Episode가 하나 이상 필요합니다.");
    log.info(
        "Episode 전처리 입력 확인 완료: trainingJobId={}, jobId={}, episodeCount={}, workRoot={}",
        claim.job().id(),
        jobId,
        episodes.size(),
        root);
    for (LocalEpisode episode : episodes) {
      checkInterrupted();
      log.info(
          "Episode 압축 해제 시작: trainingJobId={}, episodeId={}, inputOrder={}",
          claim.job().id(),
          episode.episodeId(),
          episode.inputOrder());
      extractor.extract(
          episode,
          root.resolve(
                  episode.inputRole()
                          == com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels
                              .TrainingEpisodeRole.MAPPING
                      ? "raw_mapping"
                      : "raw")
              .resolve(sessionDirectory(episode)));
    }
    log.info(
        "Episode 압축 해제 완료: trainingJobId={}, episodeCount={}", claim.job().id(), episodes.size());
    boolean acquired = false;
    try {
      log.info("UMI 전처리 실행 슬롯 대기: trainingJobId={}, jobId={}", claim.job().id(), jobId);
      singleWorker.acquire();
      acquired = true;
      log.info("UMI 전처리 실행 슬롯 획득: trainingJobId={}, jobId={}", claim.job().id(), jobId);
      JobStatus existing = client.get(jobId).orElse(null);
      if (existing == null || !"ready".equalsIgnoreCase(existing.status())) {
        log.info(
            "UMI 전처리 작업 생성 요청: trainingJobId={}, jobId={}, previousStatus={}, calibrationProfile={}, requireMapping={}",
            claim.job().id(),
            jobId,
            existing == null ? null : existing.status(),
            properties.calibrationProfile(),
            true);
        String mappingSessionId = sessionDirectory(mappings.getFirst());
        List<String> demonstrationSessionIds =
            demonstrations.stream()
                .map(EpisodePreprocessingService::sessionDirectory)
                .collect(Collectors.toList());
        client.start(
            new StartRequest(
                jobId,
                "dataset_" + claim.attempt(),
                properties.calibrationProfile(),
                true,
                mappingSessionId,
                demonstrationSessionIds));
        log.info("UMI 전처리 작업 생성 완료: trainingJobId={}, jobId={}", claim.job().id(), jobId);
      } else {
        log.info("기존 UMI 전처리 완료 결과 재사용: trainingJobId={}, jobId={}", claim.job().id(), jobId);
      }
      Instant deadline = Instant.now().plus(properties.processingTimeout());
      String lastStatus = null;
      while (Instant.now().isBefore(deadline)) {
        JobStatus status = client.get(jobId).orElse(null);
        String currentStatus = status == null ? "NOT_FOUND" : status.status();
        if (!java.util.Objects.equals(lastStatus, currentStatus)) {
          log.info(
              "UMI 전처리 상태 변경: trainingJobId={}, jobId={}, status={}, elapsedMs={}",
              claim.job().id(),
              jobId,
              currentStatus,
              Duration.between(startedAt, Instant.now()).toMillis());
          lastStatus = currentStatus;
        }
        if (status != null && "ready".equalsIgnoreCase(status.status())) {
          log.info("UMI 전처리 산출물 검증 시작: trainingJobId={}, jobId={}", claim.job().id(), jobId);
          PreprocessedTrainingInput verified = verifier.verify(status.artifact(), status.report());
          log.info(
              "UMI 전처리 산출물 검증 완료: trainingJobId={}, jobId={}, storageKey={}, reportStorageKey={}, episodeCount={}, sizeBytes={}",
              claim.job().id(),
              jobId,
              verified.storageKey(),
              verified.reportStorageKey(),
              verified.episodeCount(),
              verified.sizeBytes());
          return verified;
        }
        if (status != null
            && ("failed".equalsIgnoreCase(status.status())
                || "cancelled".equalsIgnoreCase(status.status()))) {
          log.error(
              "UMI 전처리 작업 종료 오류: trainingJobId={}, jobId={}, status={}, error={}",
              claim.job().id(),
              jobId,
              status.status(),
              status.error());
          throw new IllegalStateException(
              "Episode 전처리 실패: status=" + status.status() + ", error=" + status.error());
        }
        Thread.sleep(properties.pollInterval());
      }
      log.error(
          "UMI 전처리 제한 시간 초과: trainingJobId={}, jobId={}, timeout={}",
          claim.job().id(),
          jobId,
          properties.processingTimeout());
      client.cancel(jobId);
      throw new IllegalStateException("Episode 전처리 제한 시간을 초과했습니다.");
    } catch (InterruptedException e) {
      // Do not call HTTP with the interrupt flag set; the caller performs cancellation cleanup.
      throw new java.util.concurrent.CancellationException("Episode 전처리가 중단되었습니다.");
    } finally {
      if (acquired) {
        singleWorker.release();
        log.info("UMI 전처리 실행 슬롯 반환: trainingJobId={}, jobId={}", claim.job().id(), jobId);
      }
    }
  }

  public void cancel(java.util.UUID trainingJobId, int attempt) {
    String jobId = trainingJobId + "_" + attempt;
    log.info(
        "UMI 전처리 취소 요청: trainingJobId={}, attempt={}, jobId={}", trainingJobId, attempt, jobId);
    try {
      client.cancel(jobId);
      log.info(
          "UMI 전처리 취소 요청 완료: trainingJobId={}, attempt={}, jobId={}",
          trainingJobId,
          attempt,
          jobId);
    } catch (RuntimeException exception) {
      log.error(
          "UMI 전처리 취소 요청 오류: trainingJobId={}, attempt={}, jobId={}, message={}",
          trainingJobId,
          attempt,
          jobId,
          exception.getMessage(),
          exception);
      throw exception;
    }
  }

  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted())
      throw new java.util.concurrent.CancellationException("Episode 전처리가 중단되었습니다.");
  }

  private static String sessionDirectory(LocalEpisode episode) {
    return String.format("rec_%03d_%s", episode.inputOrder(), episode.episodeId());
  }
}
