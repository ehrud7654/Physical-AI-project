package com.anssa8bu.ssafy_gpu.worker;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class WorkerRuntimeState {
  private final AtomicReference<UUID> storageNodeId = new AtomicReference<>();
  private final AtomicInteger activeUploads = new AtomicInteger();
  private final AtomicInteger activeTrainings = new AtomicInteger();

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
}
