package com.anssa8bu.ssafy_gpu.training.storage;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.common.file.FileHashCalculator;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.EpisodeData;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class EpisodeArchiveDownloader {
  private static final Logger log = LoggerFactory.getLogger(EpisodeArchiveDownloader.class);
  private final HttpClient client;
  private final FileHashCalculator hashes;
  private final WorkerProperties properties;

  public EpisodeArchiveDownloader(
      HttpClient presignedHttpClient, FileHashCalculator hashes, WorkerProperties properties) {
    this.client = presignedHttpClient;
    this.hashes = hashes;
    this.properties = properties;
  }

  public List<LocalEpisode> downloadAll(ClaimResponse claim) {
    Path directory =
        properties
            .storageRoot()
            .resolve("training-inputs")
            .resolve(claim.job().id().toString())
            .resolve(Integer.toString(claim.attempt()))
            .toAbsolutePath()
            .normalize();
    try {
      Files.createDirectories(directory);
      List<LocalEpisode> downloaded = new ArrayList<>();
      for (int index = 0; index < claim.job().episodes().size(); index++) {
        downloaded.add(download(claim.job().episodes().get(index), index, directory));
      }
      return List.copyOf(downloaded);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Episode 다운로드 준비에 실패했습니다.", e);
    }
  }

  private LocalEpisode download(EpisodeData episode, int inputOrder, Path directory)
      throws Exception {
    var archive = episode.archive();
    Path target = directory.resolve(episode.episodeId() + ".zip").normalize();
    if (!target.getParent().equals(directory))
      throw new IllegalStateException("허용되지 않은 Episode 경로입니다.");
    Path temporary = directory.resolve(episode.episodeId() + ".part").normalize();

    if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
      verify(target, archive.sizeBytes(), archive.sha256());
      return localEpisode(episode, inputOrder, target);
    }
    if (!archive.expiresAt().isAfter(Instant.now()))
      throw new IllegalStateException("Episode Presigned URL이 만료되었습니다: " + episode.episodeId());

    log.info("Episode 다운로드 시작: episodeId={}, inputOrder={}", episode.episodeId(), inputOrder);
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(archive.downloadUrl())).GET().build();
      HttpResponse<Path> response =
          client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("Episode 다운로드 실패: HTTP " + response.statusCode());
      verify(temporary, archive.sizeBytes(), archive.sha256());
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      String storageKey = localEpisode(episode, inputOrder, target).storageKey();
      log.info(
          "Episode 다운로드 및 검증 완료: episodeId={}, sizeBytes={}, storageKey={}",
          episode.episodeId(),
          archive.sizeBytes(),
          storageKey);
      return localEpisode(episode, inputOrder, target);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private LocalEpisode localEpisode(EpisodeData episode, int inputOrder, Path target) {
    String storageKey = properties.storageRoot().toAbsolutePath().normalize().relativize(target)
        .toString().replace('\\', '/');
    return new LocalEpisode(inputOrder, episode.episodeId(), episode.inputRole(), storageKey,
        episode.archive().sha256(), episode.contractVersion(), target);
  }

  private void verify(Path file, long expectedSize, String expectedSha256) throws Exception {
    long actualSize = Files.size(file);
    if (actualSize != expectedSize)
      throw new IllegalStateException(
          "Episode 크기가 일치하지 않습니다: expected=" + expectedSize + ", actual=" + actualSize);
    String actualSha256 = hashes.sha256(file);
    if (!actualSha256.equalsIgnoreCase(expectedSha256))
      throw new IllegalStateException("Episode SHA-256이 일치하지 않습니다.");
  }

  public record LocalEpisode(
      int inputOrder,
      UUID episodeId,
      com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.TrainingEpisodeRole inputRole,
      String storageKey,
      String sha256,
      String contractVersion,
      Path path) {}
}
