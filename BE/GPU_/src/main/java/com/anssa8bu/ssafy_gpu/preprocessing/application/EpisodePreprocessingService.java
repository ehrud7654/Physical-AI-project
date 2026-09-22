package com.anssa8bu.ssafy_gpu.preprocessing.application;

import static com.anssa8bu.ssafy_gpu.preprocessing.dto.UmiPreprocessorModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.preprocessing.client.UmiPreprocessorClient;
import com.anssa8bu.ssafy_gpu.preprocessing.config.UmiPreprocessorProperties;
import com.anssa8bu.ssafy_gpu.preprocessing.storage.*;
import com.anssa8bu.ssafy_gpu.preprocessing.storage.PreprocessedArtifactVerifier.PreprocessedTrainingInput;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.TrainingEpisodeRole;
import com.anssa8bu.ssafy_gpu.training.storage.EpisodeArchiveDownloader.LocalEpisode;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.Semaphore;
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

  public EpisodePreprocessingService(WorkerProperties worker, UmiPreprocessorProperties properties,
      UmiPreprocessorClient client, SafeEpisodeArchiveExtractor extractor,
      PreprocessedArtifactVerifier verifier) {
    this.worker = worker; this.properties = properties; this.client = client;
    this.extractor = extractor; this.verifier = verifier;
  }

  public PreprocessedTrainingInput preprocess(ClaimResponse claim, List<LocalEpisode> episodes) {
    String jobId = claim.job().id() + "_" + claim.attempt();
    Path root = worker.storageRoot().resolve("preprocessing-jobs").resolve(jobId).toAbsolutePath().normalize();
    Path allowed = worker.storageRoot().resolve("preprocessing-jobs").toAbsolutePath().normalize();
    if (!root.startsWith(allowed)) throw new IllegalStateException("허용되지 않은 전처리 작업 경로입니다.");
    List<LocalEpisode> demonstrations =
        episodes.stream().filter(e -> e.inputRole() == TrainingEpisodeRole.DEMONSTRATION).toList();
    List<LocalEpisode> mappings =
        episodes.stream().filter(e -> e.inputRole() == TrainingEpisodeRole.MAPPING).toList();
    if (demonstrations.isEmpty())
      throw new IllegalStateException("전처리에는 하나 이상의 시연 Episode가 필요합니다.");
    if (mappings.size() > 1)
      throw new IllegalStateException("전처리에는 공간 매핑 Episode를 하나만 사용할 수 있습니다.");
    for (LocalEpisode episode : mappings) {
      extractor.extract(
          episode,
          root.resolve("raw_mapping")
              .resolve(String.format("rec_%03d_%s", episode.inputOrder(), episode.episodeId())));
    }
    for (LocalEpisode episode : demonstrations) {
      extractor.extract(
          episode,
          root.resolve("raw")
              .resolve(String.format("rec_%03d_%s", episode.inputOrder(), episode.episodeId())));
    }
    log.info(
        "Episode 역할별 압축 해제 완료: trainingJobId={}, mappingCount={}, demonstrationCount={}",
        claim.job().id(), mappings.size(), demonstrations.size());
    boolean acquired = false;
    try {
      singleWorker.acquire(); acquired = true;
      JobStatus existing = client.get(jobId).orElse(null);
      if (existing == null || !"ready".equalsIgnoreCase(existing.status())) {
        client.start(new StartRequest(jobId, "dataset_" + claim.attempt(),
            properties.calibrationProfile(), properties.requireMapping()));
      }
      Instant deadline = Instant.now().plus(properties.processingTimeout());
      while (Instant.now().isBefore(deadline)) {
        JobStatus status = client.get(jobId).orElse(null);
        if (status != null && "ready".equalsIgnoreCase(status.status())) {
          log.info("Episode 전처리 완료: trainingJobId={}, jobId={}", claim.job().id(), jobId);
          return verifier.verify(status.artifact(), demonstrations.size());
        }
        if (status != null && ("failed".equalsIgnoreCase(status.status()) || "cancelled".equalsIgnoreCase(status.status())))
          throw new IllegalStateException("Episode 전처리 실패: status=" + status.status() + ", error=" + status.error());
        Thread.sleep(properties.pollInterval());
      }
      client.cancel(jobId);
      throw new IllegalStateException("Episode 전처리 제한 시간을 초과했습니다.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); client.cancel(jobId);
      throw new IllegalStateException("Episode 전처리가 중단되었습니다.", e);
    } finally {
      if (acquired) singleWorker.release();
    }
  }
}
