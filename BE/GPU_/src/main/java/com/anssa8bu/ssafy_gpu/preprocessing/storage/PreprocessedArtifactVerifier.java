package com.anssa8bu.ssafy_gpu.preprocessing.storage;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.common.file.FileHashCalculator;
import com.anssa8bu.ssafy_gpu.preprocessing.dto.UmiPreprocessorModels.Artifact;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PreprocessedArtifactVerifier {
  private final WorkerProperties worker;
  private final FileHashCalculator hashes;
  private final ObjectMapper objectMapper;

  public PreprocessedArtifactVerifier(
      WorkerProperties worker, FileHashCalculator hashes, ObjectMapper objectMapper) {
    this.worker = worker;
    this.hashes = hashes;
    this.objectMapper = objectMapper;
  }

  public PreprocessedTrainingInput verify(Artifact artifact, String pipelineReportValue) {
    if (artifact == null
        || artifact.dataset() == null
        || artifact.datasetSha256() == null
        || artifact.report() == null
        || artifact.reportSha256() == null
        || pipelineReportValue == null
        || pipelineReportValue.isBlank())
      throw new IllegalStateException("전처리 완료 응답에 산출물 정보가 없습니다.");
    try {
      Path root = worker.storageRoot().toAbsolutePath().normalize();
      Path dataset = resolveArtifact(root, artifact.dataset());
      Path report = resolveArtifact(root, artifact.report());
      Path pipelineReport = resolveArtifact(root, pipelineReportValue);
      String datasetHash = verifyHash(dataset, artifact.datasetSha256(), "Zarr");
      String reportHash = verifyHash(report, artifact.reportSha256(), "전처리 보고서");
      Map<String, Object> reportValue = objectMapper.readValue(report.toFile(), Map.class);
      Map<String, Object> pipelineReportContent =
          objectMapper.readValue(pipelineReport.toFile(), Map.class);
      int episodeCount = validateReport(reportValue, pipelineReportContent);
      return new PreprocessedTrainingInput(
          storageKey(root, dataset),
          Files.size(dataset),
          datasetHash,
          storageKey(root, report),
          reportHash,
          episodeCount,
          "ready",
          "ZARR_ZIP",
          String.valueOf(reportValue.get("world_frame")));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("전처리 산출물 검증에 실패했습니다.", e);
    }
  }

  private Path resolveArtifact(Path root, String value) {
    Path path = Path.of(value).toAbsolutePath().normalize();
    if (!path.startsWith(root)
        || Files.isSymbolicLink(path)
        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalStateException("전처리 산출물 경로가 공유 저장소를 벗어났습니다.");
    return path;
  }

  private String verifyHash(Path path, String expected, String label) throws java.io.IOException {
    String actual = hashes.sha256(path).toLowerCase();
    if (!actual.equalsIgnoreCase(expected))
      throw new IllegalStateException(label + " SHA-256이 일치하지 않습니다.");
    return actual;
  }

  private int validateReport(Map<String, Object> report, Map<String, Object> pipelineReport) {
    if (!"pass".equals(report.get("status")))
      throw new IllegalStateException("Zarr 보고서 status가 pass가 아닙니다.");
    if (!"ready".equals(report.get("training_input_status")))
      throw new IllegalStateException("Zarr training_input_status가 ready가 아닙니다.");
    if (!(report.get("episodes") instanceof Number count) || count.intValue() < 1)
      throw new IllegalStateException("Zarr Episode 수는 한 개 이상이어야 합니다.");
    int episodeCount = count.intValue();
    if (!(report.get("episode_ends") instanceof List<?> ends) || ends.size() != episodeCount)
      throw new IllegalStateException("Zarr episode_ends가 올바르지 않습니다.");
    long previous = 0;
    for (Object value : ends) {
      if (!(value instanceof Number end) || end.longValue() <= previous)
        throw new IllegalStateException("Zarr episode_ends는 양의 단조 증가 값이어야 합니다.");
      previous = end.longValue();
    }
    if (!(report.get("frames") instanceof Number frames)
        || frames.longValue() < 1
        || previous != frames.longValue())
      throw new IllegalStateException("Zarr 마지막 episode_end와 전체 프레임 수가 일치하지 않습니다.");
    if (!(pipelineReport.get("included") instanceof Number included)
        || included.intValue() != episodeCount)
      throw new IllegalStateException("pipeline_report included와 Zarr Episode 수가 일치하지 않습니다.");
    if (report.get("world_frame") == null)
      throw new IllegalStateException("Zarr 보고서에 world_frame이 없습니다.");
    return episodeCount;
  }

  private String storageKey(Path root, Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }

  public record PreprocessedTrainingInput(
      String storageKey,
      long sizeBytes,
      String sha256,
      String reportStorageKey,
      String reportSha256,
      int episodeCount,
      String trainingInputStatus,
      String format,
      String worldFrame) {}
}
