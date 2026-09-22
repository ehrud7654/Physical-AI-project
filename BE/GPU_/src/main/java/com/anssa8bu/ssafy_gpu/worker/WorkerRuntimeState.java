package com.anssa8bu.ssafy_gpu.worker;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class WorkerRuntimeState {
  private final AtomicReference<UUID> storageNodeId = new AtomicReference<>();
  private final AtomicInteger activeUploads = new AtomicInteger();
  private final AtomicInteger activeTrainings = new AtomicInteger();
  private final ConcurrentHashMap<Integer, ActiveTraining> trainingsByGpu =
      new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final Path journalPath;

  public WorkerRuntimeState(ObjectMapper objectMapper, WorkerProperties properties) {
    this.objectMapper = objectMapper;
    this.journalPath =
        properties
            .storageRoot()
            .resolve("journal/gpu-spring/active-trainings.json")
            .toAbsolutePath()
            .normalize();
  }

  public record ActiveTraining(
      int gpuIndex,
      UUID trainingJobId,
      int attempt,
      String leaseToken,
      Instant leaseExpiresAt,
      String runId,
      long lastForwardedSequence) {
    public ActiveTraining(
        int gpuIndex, UUID trainingJobId, int attempt, String leaseToken, Instant leaseExpiresAt) {
      this(gpuIndex, trainingJobId, attempt, leaseToken, leaseExpiresAt, null, 0L);
    }
  }

  @PostConstruct
  void restore() {
    if (!Files.isRegularFile(journalPath)) return;
    try {
      List<ActiveTraining> restored =
          objectMapper.readValue(journalPath.toFile(), new TypeReference<>() {});
      restored.forEach(value -> trainingsByGpu.put(value.gpuIndex(), value));
      activeTrainings.set(trainingsByGpu.size());
    } catch (RuntimeException exception) {
      throw new IllegalStateException("GPU Spring 활성 학습 journal 복구에 실패했습니다.", exception);
    }
  }

  public UUID storageNodeId() {
    return storageNodeId.get();
  }

  public void storageNodeId(UUID value) {
    storageNodeId.set(value);
  }

  public int activeUploads() {
    return activeUploads.get();
  }

  public int activeTrainings() {
    return activeTrainings.get();
  }

  public void uploadStarted() {
    activeUploads.incrementAndGet();
  }

  public void uploadFinished() {
    activeUploads.updateAndGet(v -> Math.max(0, v - 1));
  }

  public void trainingStarted() {
    activeTrainings.incrementAndGet();
  }

  public void trainingFinished() {
    activeTrainings.updateAndGet(v -> Math.max(0, v - 1));
  }

  public boolean isGpuBusy(int gpuIndex) {
    return trainingsByGpu.containsKey(gpuIndex);
  }

  public boolean activate(ActiveTraining training) {
    boolean added = trainingsByGpu.putIfAbsent(training.gpuIndex(), training) == null;
    if (added) {
      trainingStarted();
      persist();
    }
    return added;
  }

  public void renew(int gpuIndex, Instant leaseExpiresAt) {
    trainingsByGpu.computeIfPresent(
        gpuIndex,
        (ignored, current) ->
            new ActiveTraining(
                current.gpuIndex(),
                current.trainingJobId(),
                current.attempt(),
                current.leaseToken(),
                leaseExpiresAt,
                current.runId(),
                current.lastForwardedSequence()));
    persist();
  }

  public void bindRun(int gpuIndex, String runId) {
    trainingsByGpu.computeIfPresent(
        gpuIndex,
        (ignored, current) ->
            new ActiveTraining(
                current.gpuIndex(),
                current.trainingJobId(),
                current.attempt(),
                current.leaseToken(),
                current.leaseExpiresAt(),
                runId,
                current.lastForwardedSequence()));
    persist();
  }

  public void forwarded(int gpuIndex, long sequence) {
    trainingsByGpu.computeIfPresent(
        gpuIndex,
        (ignored, current) ->
            new ActiveTraining(
                current.gpuIndex(),
                current.trainingJobId(),
                current.attempt(),
                current.leaseToken(),
                current.leaseExpiresAt(),
                current.runId(),
                sequence));
    persist();
  }

  public ActiveTraining require(UUID trainingJobId, int attempt) {
    return trainingsByGpu.values().stream()
        .filter(value -> value.trainingJobId().equals(trainingJobId) && value.attempt() == attempt)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("활성 학습 할당을 찾을 수 없습니다."));
  }

  public void release(int gpuIndex) {
    if (trainingsByGpu.remove(gpuIndex) != null) {
      trainingFinished();
      persist();
    }
  }

  public void release(int gpuIndex, UUID jobId, int attempt) {
    var removed = new java.util.concurrent.atomic.AtomicBoolean();
    trainingsByGpu.computeIfPresent(gpuIndex, (ignored, current) -> {
      if (current.trainingJobId().equals(jobId) && current.attempt() == attempt) {
        removed.set(true);
        return null;
      }
      return current;
    });
    if (removed.get()) {
      trainingFinished();
      persist();
    }
  }

  public List<ActiveTraining> activeTrainingAssignments() {
    return List.copyOf(trainingsByGpu.values());
  }

  private synchronized void persist() {
    try {
      Files.createDirectories(journalPath.getParent());
      Path temporary = journalPath.resolveSibling(journalPath.getFileName() + ".tmp");
      objectMapper.writeValue(temporary.toFile(), activeTrainingAssignments());
      try {
        Files.move(
            temporary,
            journalPath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, journalPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("GPU Spring 활성 학습 journal 저장에 실패했습니다.", exception);
    }
  }
}
