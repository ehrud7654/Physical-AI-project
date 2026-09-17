package com.anssa8bu.ssafy_gpu.worker;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class WorkerRuntimeState {
  private final AtomicReference<UUID> storageNodeId = new AtomicReference<>();
  private final AtomicInteger activeUploads = new AtomicInteger();
  private final AtomicInteger activeTrainings = new AtomicInteger();
  private final ConcurrentHashMap<Integer, ActiveTraining> trainingsByGpu = new ConcurrentHashMap<>();

  public record ActiveTraining(
      int gpuIndex,
      UUID trainingJobId,
      int attempt,
      String leaseToken,
      Instant leaseExpiresAt,
      String runId,
      long lastForwardedSequence) {
    public ActiveTraining(
        int gpuIndex,
        UUID trainingJobId,
        int attempt,
        String leaseToken,
        Instant leaseExpiresAt) {
      this(gpuIndex, trainingJobId, attempt, leaseToken, leaseExpiresAt, null, 0L);
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
    if (added) trainingStarted();
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
  }

  public void bindRun(int gpuIndex, String runId) {
    trainingsByGpu.computeIfPresent(
        gpuIndex,
        (ignored, current) ->
            new ActiveTraining(
                current.gpuIndex(), current.trainingJobId(), current.attempt(),
                current.leaseToken(), current.leaseExpiresAt(), runId,
                current.lastForwardedSequence()));
  }

  public void forwarded(int gpuIndex, long sequence) {
    trainingsByGpu.computeIfPresent(
        gpuIndex,
        (ignored, current) ->
            new ActiveTraining(
                current.gpuIndex(), current.trainingJobId(), current.attempt(),
                current.leaseToken(), current.leaseExpiresAt(), current.runId(), sequence));
  }

  public ActiveTraining require(UUID trainingJobId, int attempt) {
    return trainingsByGpu.values().stream()
        .filter(value -> value.trainingJobId().equals(trainingJobId) && value.attempt() == attempt)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("활성 학습 할당을 찾을 수 없습니다."));
  }

  public void release(int gpuIndex) {
    if (trainingsByGpu.remove(gpuIndex) != null) trainingFinished();
  }

  public List<ActiveTraining> activeTrainingAssignments() {
    return List.copyOf(trainingsByGpu.values());
  }
}
