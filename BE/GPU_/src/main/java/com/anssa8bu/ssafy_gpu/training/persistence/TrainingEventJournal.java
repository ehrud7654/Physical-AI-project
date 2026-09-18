package com.anssa8bu.ssafy_gpu.training.persistence;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.EventRequest;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.EventResponse;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Durable outbox for GPU -> EC2 training events. */
@Component
public class TrainingEventJournal {
  private final Path root;
  private final ObjectMapper objectMapper;

  public TrainingEventJournal(WorkerProperties worker, ObjectMapper objectMapper) {
    this.root =
        worker.storageRoot().resolve("journal/gpu-spring/events").toAbsolutePath().normalize();
    this.objectMapper = objectMapper;
  }

  public synchronized Entry stage(UUID jobId, EventRequest request) {
    String hash = hash(request);
    Entry current = find(request.eventId());
    if (current != null) {
      if (!current.payloadHash().equals(hash)) {
        throw new IllegalStateException("같은 eventId에 다른 payload를 저장할 수 없습니다.");
      }
      return current;
    }
    Entry created = new Entry(jobId, request, hash, null, 0, Instant.now(), null);
    write(created);
    return created;
  }

  public synchronized void delivered(String eventId, EventResponse response) {
    Entry current = require(eventId);
    write(
        new Entry(
            current.jobId(),
            current.request(),
            current.payloadHash(),
            response,
            current.attempts() + 1,
            null,
            Instant.now()));
  }

  public synchronized void failed(String eventId) {
    Entry current = require(eventId);
    int attempts = current.attempts() + 1;
    long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
    write(
        new Entry(
            current.jobId(),
            current.request(),
            current.payloadHash(),
            null,
            attempts,
            Instant.now().plusSeconds(delaySeconds),
            null));
  }

  public List<Entry> retryable(Instant now) {
    if (!Files.isDirectory(root)) return List.of();
    try (var files = Files.list(root)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::read)
          .filter(entry -> entry.response() == null)
          .filter(entry -> entry.nextRetryAt() == null || !entry.nextRetryAt().isAfter(now))
          .sorted(Comparator.comparing(entry -> entry.request().occurredAt()))
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("학습 이벤트 journal 조회에 실패했습니다.", exception);
    }
  }

  public Entry find(String eventId) {
    Path path = path(eventId);
    return Files.isRegularFile(path) ? read(path) : null;
  }

  private Entry require(String eventId) {
    Entry value = find(eventId);
    if (value == null) throw new IllegalStateException("학습 이벤트 journal 항목이 없습니다.");
    return value;
  }

  private Entry read(Path path) {
    try {
      return objectMapper.readValue(path.toFile(), Entry.class);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("학습 이벤트 journal 파일을 읽지 못했습니다: " + path, exception);
    }
  }

  private void write(Entry entry) {
    try {
      Files.createDirectories(root);
      Path target = path(entry.request().eventId());
      Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
      objectMapper.writeValue(temporary.toFile(), entry);
      move(temporary, target);
    } catch (IOException exception) {
      throw new IllegalStateException("학습 이벤트 journal 저장에 실패했습니다.", exception);
    }
  }

  private Path path(String eventId) {
    String safe =
        HexFormat.of().formatHex(eventId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Path path = root.resolve(safe + ".json").normalize();
    if (!path.startsWith(root)) throw new IllegalArgumentException("eventId가 올바르지 않습니다.");
    return path;
  }

  private String hash(EventRequest request) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request)));
    } catch (Exception exception) {
      throw new IllegalStateException("학습 이벤트 payload hash 계산에 실패했습니다.", exception);
    }
  }

  private static void move(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public record Entry(
      UUID jobId,
      EventRequest request,
      String payloadHash,
      EventResponse response,
      int attempts,
      Instant nextRetryAt,
      Instant deliveredAt) {}
}
