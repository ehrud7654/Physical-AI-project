package com.anssa8bu.ssafy_gpu.checkpoint.application;

import static com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.*;

import com.anssa8bu.ssafy_gpu.checkpoint.client.CheckpointClient;
import com.anssa8bu.ssafy_gpu.checkpoint.storage.PresignedCheckpointUploader;
import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.common.file.FileHashCalculator;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckpointUploadService {
  private static final String CONTENT_TYPE = "application/octet-stream";
  private final CheckpointClient client;
  private final PresignedCheckpointUploader uploader;
  private final FileHashCalculator hashes;
  private final WorkerProperties worker;
  private final WorkerRuntimeState state;

  public CheckpointUploadService(
      CheckpointClient client,
      PresignedCheckpointUploader uploader,
      FileHashCalculator hashes,
      WorkerProperties worker,
      WorkerRuntimeState state) {
    this.client = client;
    this.uploader = uploader;
    this.hashes = hashes;
    this.worker = worker;
    this.state = state;
  }

  public UploadResult uploadAndComplete(
      UUID jobId,
      int attempt,
      String leaseToken,
      String uploadIdempotencyKey,
      String completeIdempotencyKey,
      LocalCheckpoint checkpoint) {
    Path file = validatePath(checkpoint.path());
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
              CONTENT_TYPE,
              size,
              sha256,
              checkpoint.metadata());
      UploadSessionResponse session =
          client.createUploadSession(jobId, uploadIdempotencyKey, request);
      uploader.upload(session, file, sha256);
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
      return new UploadResult(session.skillVersionId(), session.version(), size, sha256, complete);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("CKPT 파일 정보 조회에 실패했습니다.", e);
    } finally {
      state.uploadFinished();
    }
  }

  private Path validatePath(Path path) {
    Path root = worker.storageRoot().toAbsolutePath().normalize();
    Path file = path.toAbsolutePath().normalize();
    if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException("CKPT 파일은 GPU storage root 아래의 일반 파일이어야 합니다.");
    return file;
  }
}
