package com.anssa8bu.ssafy_gpu.training.cleanup;

import com.anssa8bu.ssafy_gpu.checkpoint.persistence.CheckpointTransferJournal;
import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class TrainingArtifactCleanupService {
  private static final Logger log = LoggerFactory.getLogger(TrainingArtifactCleanupService.class);
  private final Path root;
  private final WorkerRuntimeState state;
  private final TrainingArtifactCleanupProperties properties;
  private final CheckpointTransferJournal checkpoints;
  private final MeterRegistry meter;
  private final ConcurrentHashMap<String, CleanupTarget> retries = new ConcurrentHashMap<>();

  public TrainingArtifactCleanupService(
      WorkerProperties worker,
      WorkerRuntimeState state,
      TrainingArtifactCleanupProperties properties,
      CheckpointTransferJournal checkpoints,
      MeterRegistry meter) {
    root = worker.storageRoot().toAbsolutePath().normalize();
    this.state = state;
    this.properties = properties;
    this.checkpoints = checkpoints;
    this.meter = meter;
  }

  public void cleanup(UUID jobId, int attempt) {
    if (isActive(jobId, attempt) || checkpoints.isFinalizationPending(jobId, attempt)) {
      log.info("미완료 학습 artifact 정리 보류: trainingJobId={}, attempt={}", jobId, attempt);
      return;
    }
    boolean success = deleteTree(resolve("training-inputs", jobId, attempt));
    success &= deleteTree(resolve("training-results", jobId, attempt));
    String key = jobId + ":" + attempt;
    if (success) retries.remove(key);
    else retries.put(key, new CleanupTarget(jobId, attempt));
  }

  public void cleanupOrphans() {
    if (!properties.enabled()) return;
    scan(root.resolve("training-inputs"));
    scan(root.resolve("training-results"));
    retries.values().forEach(target -> cleanup(target.jobId(), target.attempt()));
  }

  private void scan(Path artifactRoot) {
    if (!Files.isDirectory(artifactRoot)) return;
    Instant threshold = Instant.now().minus(properties.orphanGracePeriod());
    try (var jobs = Files.list(artifactRoot)) {
      jobs.filter(Files::isDirectory).forEach(job -> scanAttempts(job, threshold));
    } catch (IOException exception) {
      log.warn("학습 artifact orphan 탐색 실패: root={}", artifactRoot, exception);
    }
  }

  private void scanAttempts(Path jobDirectory, Instant threshold) {
    UUID jobId;
    try {
      jobId = UUID.fromString(jobDirectory.getFileName().toString());
    } catch (IllegalArgumentException ignored) {
      return;
    }
    try (var attempts = Files.list(jobDirectory)) {
      attempts
          .filter(Files::isDirectory)
          .forEach(
              path -> {
                try {
                  int attempt = Integer.parseInt(path.getFileName().toString());
                  boolean active = isActive(jobId, attempt);
                  if (!active
                      && !checkpoints.isFinalizationPending(jobId, attempt)
                      && Files.getLastModifiedTime(path).toInstant().isBefore(threshold))
                    deleteTree(path);
                } catch (NumberFormatException | IOException ignored) {
                  // 작업 디렉터리 규칙과 다른 항목은 자동 삭제하지 않는다.
                }
              });
    } catch (IOException exception) {
      log.warn("학습 artifact attempt 탐색 실패: path={}", jobDirectory, exception);
    }
  }

  private Path resolve(String kind, UUID jobId, int attempt) {
    Path kindRoot = root.resolve(kind).normalize();
    Path path = kindRoot.resolve(jobId.toString()).resolve(Integer.toString(attempt)).normalize();
    if (!path.startsWith(kindRoot)) throw new IllegalArgumentException("허용되지 않은 경로입니다.");
    return path;
  }

  private boolean isActive(UUID jobId, int attempt) {
    return state.activeTrainingAssignments().stream()
        .anyMatch(value -> value.trainingJobId().equals(jobId) && value.attempt() == attempt);
  }

  private boolean deleteTree(Path directory) {
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return true;
    if (Files.isSymbolicLink(directory) || !directory.startsWith(root))
      throw new IllegalArgumentException("학습 artifact 삭제 경로가 안전하지 않습니다.");
    try {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if (Files.isSymbolicLink(file)) throw new IOException("symbolic link 삭제를 거부합니다.");
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure)
                throws IOException {
              if (failure != null) throw failure;
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
      log.info("학습 artifact 정리 완료: path={}", directory);
      meter.counter("gpu.training.cleanup", "result", "success").increment();
      return true;
    } catch (IOException exception) {
      log.warn("학습 artifact 정리 실패: path={}", directory, exception);
      meter.counter("gpu.training.cleanup", "result", "failure").increment();
      return false;
    }
  }

  private record CleanupTarget(UUID jobId, int attempt) {}
}
