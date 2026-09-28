package com.anssa8bu.ssafy_gpu.fastapi.persistence;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.fastapi.application.TrainingCompletionOrchestrator.CompletionResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CompletionReceiptStore {
  private final Path root;
  private final ObjectMapper objectMapper;

  public CompletionReceiptStore(WorkerProperties worker, ObjectMapper objectMapper) {
    this.root =
        worker.storageRoot().resolve("journal/gpu-spring/completions").toAbsolutePath().normalize();
    this.objectMapper = objectMapper;
  }

  public String hash(Object request) {
    try {
      byte[] canonical = objectMapper.writeValueAsBytes(request);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    } catch (Exception exception) {
      throw new IllegalStateException("완료 요청 hash 계산에 실패했습니다.", exception);
    }
  }

  public Receipt find(String eventId) {
    Path path = path(eventId);
    if (!Files.isRegularFile(path)) return null;
    try {
      return objectMapper.readValue(path.toFile(), Receipt.class);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("완료 receipt 조회에 실패했습니다.", exception);
    }
  }

  public synchronized void save(String eventId, String requestHash, CompletionResponse response) {
    try {
      Files.createDirectories(root);
      Path target = path(eventId);
      Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
      objectMapper.writeValue(temporary.toFile(), new Receipt(requestHash, response));
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("완료 receipt 저장에 실패했습니다.", exception);
    }
  }

  private Path path(String eventId) {
    String safe = HexFormat.of().formatHex(eventId.getBytes(StandardCharsets.UTF_8));
    Path path = root.resolve(safe + ".json").normalize();
    if (!path.startsWith(root)) throw new IllegalArgumentException("완료 eventId가 올바르지 않습니다.");
    return path;
  }

  public record Receipt(String requestHash, CompletionResponse response) {}
}
