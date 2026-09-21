package com.anssa8bu.ssafy_gpu.preprocessing.storage;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.common.file.FileHashCalculator;
import com.anssa8bu.ssafy_gpu.preprocessing.dto.UmiPreprocessorModels.Artifact;
import java.nio.file.*;
import org.springframework.stereotype.Component;

@Component
public class PreprocessedArtifactVerifier {
  private final WorkerProperties worker;
  private final FileHashCalculator hashes;

  public PreprocessedArtifactVerifier(WorkerProperties worker, FileHashCalculator hashes) {
    this.worker = worker;
    this.hashes = hashes;
  }

  public PreprocessedTrainingInput verify(Artifact artifact, int episodeCount) {
    if (artifact == null || artifact.archive() == null || artifact.archiveSha256() == null)
      throw new IllegalStateException("전처리 완료 응답에 산출물 정보가 없습니다.");
    try {
      Path root = worker.storageRoot().toAbsolutePath().normalize();
      Path archive = Path.of(artifact.archive()).toAbsolutePath().normalize();
      if (!archive.startsWith(root) || Files.isSymbolicLink(archive)
          || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS))
        throw new IllegalStateException("전처리 산출물 경로가 공유 저장소를 벗어났습니다.");
      String actual = hashes.sha256(archive);
      if (!actual.equalsIgnoreCase(artifact.archiveSha256()))
        throw new IllegalStateException("전처리 산출물 SHA-256이 일치하지 않습니다.");
      String key = root.relativize(archive).toString().replace('\\', '/');
      return new PreprocessedTrainingInput(key, Files.size(archive), actual.toLowerCase(),
          artifact.datasetSha256(), artifact.reportSha256(), episodeCount);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("전처리 산출물 검증에 실패했습니다.", e);
    }
  }

  public record PreprocessedTrainingInput(
      String storageKey, long sizeBytes, String sha256, String datasetSha256,
      String reportSha256, int episodeCount) {}
}
