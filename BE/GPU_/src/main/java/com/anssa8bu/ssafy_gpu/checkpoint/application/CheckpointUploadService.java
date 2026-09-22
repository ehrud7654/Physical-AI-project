package com.anssa8bu.ssafy_gpu.checkpoint.application;

import static com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.*;

import com.anssa8bu.ssafy_gpu.checkpoint.client.CheckpointClient;
import com.anssa8bu.ssafy_gpu.checkpoint.persistence.CheckpointTransferJournal;
import com.anssa8bu.ssafy_gpu.checkpoint.storage.PresignedCheckpointUploader;
import com.anssa8bu.ssafy_gpu.common.config.CheckpointUploadProperties;
import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.common.file.FileHashCalculator;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckpointUploadService {
  private final CheckpointClient client;
  private final PresignedCheckpointUploader uploader;
  private final FileHashCalculator hashes;
  private final WorkerProperties worker;
  private final WorkerRuntimeState state;
  private final CheckpointUploadProperties properties;
  private final CheckpointTransferJournal journal;

  public CheckpointUploadService(
      CheckpointClient client,
      PresignedCheckpointUploader uploader,
      FileHashCalculator hashes,
      WorkerProperties worker,
      WorkerRuntimeState state,
      CheckpointUploadProperties properties,
      CheckpointTransferJournal journal) {
    this.client = client;
    this.uploader = uploader;
    this.hashes = hashes;
    this.worker = worker;
    this.state = state;
    this.properties = properties;
    this.journal = journal;
  }

  public UploadResult uploadAndComplete(
      UUID jobId,
      int attempt,
      String leaseToken,
      String uploadIdempotencyKey,
      String completeIdempotencyKey,
      LocalCheckpoint checkpoint,
      Runnable afterUpload) {
    Path file = validatePath(checkpoint.path());
    validateMetadata(checkpoint.metadata());
    try {
      long size = Files.size(file);
      if (size <= 0) throw new IllegalArgumentException("CKPT 파일이 비어 있습니다.");
      String sha256 = hashes.sha256(file);
      state.uploadStarted();
      UploadSessionRequest request =
          new UploadSessionRequest(
              attempt,
              leaseToken,
              file.getFileName().toString(),
              checkpoint.modelCompletedAt(),
              properties.contentType(),
              size,
              sha256,
              checkpoint.metadata());
      var transfer =
          journal.stage(
              jobId, attempt, uploadIdempotencyKey, completeIdempotencyKey, file, request);
      if (transfer.completeResponse() != null) {
        return new UploadResult(
            transfer.session().skillVersionId(),
            transfer.session().version(),
            size,
            sha256,
            transfer.completeResponse());
      }
      UploadSessionResponse session = transfer.session();
      if (session == null) {
        session = client.createUploadSession(jobId, uploadIdempotencyKey, request);
        transfer = journal.session(jobId, attempt, session);
      }
      if (!transfer.s3PutCompleted()) {
        uploader.upload(session, file, sha256);
        journal.uploaded(jobId, attempt);
      }
      afterUpload.run();
      CompleteResponse complete =
          client.complete(
              jobId,
              completeIdempotencyKey,
              new CompleteRequest(
                  attempt,
                  leaseToken,
                  session.skillVersionId(),
                  new Checkpoint(size, sha256),
                  checkpoint.metadata()));
      journal.completed(jobId, attempt, complete);
      return new UploadResult(session.skillVersionId(), session.version(), size, sha256, complete);
    } catch (java.io.IOException e) {
      if (journal.find(jobId, attempt) != null) journal.failed(jobId, attempt);
      throw new IllegalStateException("CKPT 파일 정보 조회에 실패했습니다.", e);
    } catch (RuntimeException e) {
      if (journal.find(jobId, attempt) != null) journal.failed(jobId, attempt);
      throw e;
    } finally {
      state.uploadFinished();
    }
  }

  private void validateMetadata(Metadata metadata) {
    if (metadata == null) throw new IllegalArgumentException("CKPT metadata가 필요합니다.");
    if (metadata.nParams() <= 1)
      throw new IllegalArgumentException("CKPT nParams는 실제 모델에서 계산한 1보다 큰 값이어야 합니다.");
  }

  private Path validatePath(Path path) {
    Path root = worker.storageRoot().toAbsolutePath().normalize();
    Path file = path.toAbsolutePath().normalize();
    if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException("CKPT 파일은 GPU storage root 아래의 일반 파일이어야 합니다.");
    return file;
  }
}
