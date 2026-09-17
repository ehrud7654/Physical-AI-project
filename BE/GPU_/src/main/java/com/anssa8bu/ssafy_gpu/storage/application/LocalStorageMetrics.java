package com.anssa8bu.ssafy_gpu.storage.application;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import java.nio.file.*;
import org.springframework.stereotype.Component;

@Component
public class LocalStorageMetrics {
  private final WorkerProperties properties;

  public LocalStorageMetrics(WorkerProperties properties) {
    this.properties = properties;
  }

  public Capacity capacity() {
    try {
      Files.createDirectories(properties.storageRoot());
      FileStore store = Files.getFileStore(properties.storageRoot());
      return new Capacity(store.getTotalSpace(), store.getUsableSpace());
    } catch (Exception e) {
      throw new IllegalStateException("GPU 로컬 저장소 용량 조회에 실패했습니다.", e);
    }
  }

  public record Capacity(long totalBytes, long availableBytes) {}
}
