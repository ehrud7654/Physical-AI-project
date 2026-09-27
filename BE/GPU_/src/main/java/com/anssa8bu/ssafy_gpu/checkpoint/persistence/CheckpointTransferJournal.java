package com.anssa8bu.ssafy_gpu.checkpoint.persistence;

import static com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CheckpointTransferJournal {
  private final Path root;
  private final ObjectMapper mapper;

  public CheckpointTransferJournal(WorkerProperties worker, ObjectMapper mapper) {
    root =
        worker.storageRoot().resolve("journal/gpu-spring/checkpoints").toAbsolutePath().normalize();
    this.mapper = mapper;
  }

  public synchronized Transfer stage(
      UUID jobId,
      int attempt,
      String uploadKey,
      String completeKey,
      Path localFile,
      UploadSessionRequest request) {
    Transfer current = find(jobId, attempt);
    if (current != null) return current;
    Transfer value =
        new Transfer(
            jobId,
            attempt,
            uploadKey,
            completeKey,
            localFile.toAbsolutePath().normalize().toString(),
            request,
            null,
            false,
            null,
            0,
            Instant.now());
    write(value);
    return value;
  }

  public synchronized Transfer session(UUID jobId, int attempt, UploadSessionResponse session) {
    return update(require(jobId, attempt), session, null, null);
  }

  public synchronized Transfer uploaded(UUID jobId, int attempt) {
    return update(require(jobId, attempt), null, true, null);
  }

  public synchronized Transfer completed(UUID jobId, int attempt, CompleteResponse response) {
    return update(require(jobId, attempt), null, null, response);
  }

  public synchronized void failed(UUID jobId, int attempt) {
    Transfer current = require(jobId, attempt);
    write(
        new Transfer(
            current.jobId(),
            current.attempt(),
            current.uploadIdempotencyKey(),
            current.completeIdempotencyKey(),
            current.localFile(),
            current.request(),
            current.session(),
            current.s3PutCompleted(),
            current.completeResponse(),
            current.retryCount() + 1,
            Instant.now()));
  }

  public Transfer find(UUID jobId, int attempt) {
    Path path = path(jobId, attempt);
    if (!Files.isRegularFile(path)) return null;
    try {
      return mapper.readValue(path.toFile(), Transfer.class);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("CKPT 전송 journal 조회에 실패했습니다.", exception);
    }
  }

  public boolean isFinalizationPending(UUID jobId, int attempt) {
    Transfer transfer = find(jobId, attempt);
    return transfer != null && transfer.completeResponse() == null;
  }

  private Transfer update(
      Transfer current,
      UploadSessionResponse session,
      Boolean uploaded,
      CompleteResponse completed) {
    Transfer value =
        new Transfer(
            current.jobId(),
            current.attempt(),
            current.uploadIdempotencyKey(),
            current.completeIdempotencyKey(),
            current.localFile(),
            current.request(),
            session == null ? current.session() : session,
            uploaded == null ? current.s3PutCompleted() : uploaded,
            completed == null ? current.completeResponse() : completed,
            current.retryCount(),
            Instant.now());
    write(value);
    return value;
  }

  private Transfer require(UUID jobId, int attempt) {
    Transfer value = find(jobId, attempt);
    if (value == null) throw new IllegalStateException("CKPT 전송 journal 항목이 없습니다.");
    return value;
  }

  private void write(Transfer value) {
    try {
      Files.createDirectories(root);
      Path target = path(value.jobId(), value.attempt());
      Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
      mapper.writeValue(temporary.toFile(), value);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("CKPT 전송 journal 저장에 실패했습니다.", exception);
    }
  }

  private Path path(UUID jobId, int attempt) {
    return root.resolve(jobId + "-" + attempt + ".json").normalize();
  }

  public record Transfer(
      UUID jobId,
      int attempt,
      String uploadIdempotencyKey,
      String completeIdempotencyKey,
      String localFile,
      UploadSessionRequest request,
      UploadSessionResponse session,
      boolean s3PutCompleted,
      CompleteResponse completeResponse,
      int retryCount,
      Instant updatedAt) {}
}
