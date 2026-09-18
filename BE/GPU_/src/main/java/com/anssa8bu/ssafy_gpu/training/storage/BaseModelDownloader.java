package com.anssa8bu.ssafy_gpu.training.storage;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.common.file.FileHashCalculator;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class BaseModelDownloader {
  private static final Logger log = LoggerFactory.getLogger(BaseModelDownloader.class);
  private final HttpClient client;
  private final FileHashCalculator hashes;
  private final WorkerProperties properties;

  public BaseModelDownloader(
      HttpClient presignedHttpClient, FileHashCalculator hashes, WorkerProperties properties) {
    this.client = presignedHttpClient;
    this.hashes = hashes;
    this.properties = properties;
  }

  public LocalBaseModel download(ClaimResponse claim) {
    if (claim.job().trainingMode() != TrainingMode.FINE_TUNE) return null;
    BaseModelData model = claim.job().baseModel();
    if (model == null) throw new IllegalStateException("FINE_TUNE 작업에 기준 모델이 없습니다.");
    if (!model.expiresAt().isAfter(Instant.now()))
      throw new IllegalStateException("기준 모델 Presigned URL이 만료되었습니다.");
    Path directory = properties.storageRoot().resolve("training-inputs")
        .resolve(claim.job().id().toString()).resolve(Integer.toString(claim.attempt()))
        .resolve("base-model").toAbsolutePath().normalize();
    Path target = directory.resolve("model.ckpt").normalize();
    Path temporary = directory.resolve("model.ckpt.part").normalize();
    try {
      Files.createDirectories(directory);
      log.info("기준 모델 다운로드 시작: skillVersionId={}, version={}",
          model.skillVersionId(), model.version());
      HttpResponse<Path> response = client.send(
          HttpRequest.newBuilder(URI.create(model.downloadUrl())).GET().build(),
          HttpResponse.BodyHandlers.ofFile(temporary));
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("기준 모델 다운로드 실패: HTTP " + response.statusCode());
      long actualSize = Files.size(temporary);
      if (actualSize != model.sizeBytes())
        throw new IllegalStateException(
            "기준 모델 크기가 일치하지 않습니다: expected=" + model.sizeBytes()
                + ", actual=" + actualSize);
      String actualSha256 = hashes.sha256(temporary);
      if (!actualSha256.equalsIgnoreCase(model.sha256()))
        throw new IllegalStateException("기준 모델 SHA-256이 일치하지 않습니다.");
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      String storageKey = properties.storageRoot().toAbsolutePath().normalize().relativize(target)
          .toString().replace('\\', '/');
      log.info("기준 모델 다운로드 및 검증 완료: skillVersionId={}, storageKey={}",
          model.skillVersionId(), storageKey);
      return new LocalBaseModel(model.skillVersionId(), model.version(), storageKey,
          model.sha256(), target);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("기준 모델 다운로드 또는 검증에 실패했습니다.", e);
    } finally {
      try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
    }
  }

  public record LocalBaseModel(
      java.util.UUID skillVersionId, int version, String storageKey, String sha256, Path path) {}
}
