package com.anssa8bu.ssafy_gpu.fastapi.application;

import static com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.*;

import com.anssa8bu.ssafy_gpu.checkpoint.application.CheckpointUploadService;
import com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.LocalCheckpoint;
import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.fastapi.persistence.CompletionReceiptStore;
import com.anssa8bu.ssafy_gpu.training.application.TrainingEventService;
import com.anssa8bu.ssafy_gpu.training.cleanup.TrainingArtifactCleanupService;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class TrainingCompletionOrchestrator {
  private final CheckpointUploadService checkpoints;
  private final TrainingEventService events;
  private final WorkerRuntimeState state;
  private final WorkerProperties worker;
  private final TrainingArtifactCleanupService cleanup;
  private final CompletionReceiptStore receipts;
  private final java.util.concurrent.ConcurrentHashMap<String, Long> completionSequences =
      new java.util.concurrent.ConcurrentHashMap<>();

  public TrainingCompletionOrchestrator(
      CheckpointUploadService checkpoints,
      TrainingEventService events,
      WorkerRuntimeState state,
      WorkerProperties worker,
      TrainingArtifactCleanupService cleanup,
      CompletionReceiptStore receipts) {
    this.checkpoints = checkpoints;
    this.events = events;
    this.state = state;
    this.worker = worker;
    this.cleanup = cleanup;
    this.receipts = receipts;
  }

  public synchronized CompletionResponse complete(String runId, CompletionRequest request) {
    String requestHash = receipts.hash(request);
    var receipt = receipts.find(request.eventId());
    if (receipt != null) {
      if (!receipt.requestHash().equals(requestHash))
        throw new IllegalStateException("같은 eventId에 다른 완료 payload가 요청되었습니다.");
      return receipt.response();
    }
    var assignment = state.require(request.trainingJobId(), request.attempt());
    if (assignment.runId() == null || !assignment.runId().equals(runId))
      throw new IllegalStateException("완료 콜백의 runId가 활성 학습과 일치하지 않습니다.");
    long eventSequence =
        completionSequences.computeIfAbsent(
            request.eventId(), ignored -> assignment.lastForwardedSequence() + 2);
    events.trainingFinished(
        request.trainingJobId(),
        request.attempt(),
        assignment.leaseToken(),
        request.eventId() + "-finished",
        eventSequence,
        request.result().sizeBytes(),
        request.result().sha256(),
        request.modelCompletedAt());
    Path checkpoint =
        worker.storageRoot().resolve(request.result().checkpointStorageKey()).normalize();
    var uploaded =
        checkpoints.uploadAndComplete(
            request.trainingJobId(),
            request.attempt(),
            assignment.leaseToken(),
            request.eventId() + "-upload",
            request.eventId() + "-complete",
            new LocalCheckpoint(
                checkpoint, request.modelCompletedAt(), request.result().metadata()));
    CompletionResponse response =
        new CompletionResponse(
            uploaded.skillVersionId(), uploaded.version(), uploaded.completion().status());
    receipts.save(request.eventId(), requestHash, response);
    state.release(assignment.gpuIndex());
    cleanup.cleanup(request.trainingJobId(), request.attempt());
    return response;
  }

  public CompletionResponse completeRecovered(RunResponse run) {
    if (!"COMPLETED".equals(run.status()) || run.result() == null)
      throw new IllegalArgumentException("완료된 FastAPI run만 복구할 수 있습니다.");
    return complete(
        run.runId(),
        new CompletionRequest(
            "fastapi-" + run.runId() + "-completion",
            run.trainingJobId(),
            run.attempt(),
            run.modelCompletedAt(),
            run.result()));
  }

  public record CompletionResponse(java.util.UUID skillVersionId, int version, String status) {}
}
