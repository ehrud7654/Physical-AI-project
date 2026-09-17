package com.anssa8bu.ssafy_gpu.fastapi.application;

import static com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.*;

import com.anssa8bu.ssafy_gpu.checkpoint.application.CheckpointUploadService;
import com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.LocalCheckpoint;
import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.training.application.TrainingEventService;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TrainingCompletionOrchestrator {
  private final CheckpointUploadService checkpoints;
  private final TrainingEventService events;
  private final WorkerRuntimeState state;
  private final WorkerProperties worker;
  private final ConcurrentHashMap<String, CompletionResponse> completed = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> completionSequences = new ConcurrentHashMap<>();

  public TrainingCompletionOrchestrator(
      CheckpointUploadService checkpoints,
      TrainingEventService events,
      WorkerRuntimeState state,
      WorkerProperties worker) {
    this.checkpoints = checkpoints;
    this.events = events;
    this.state = state;
    this.worker = worker;
  }

  public synchronized CompletionResponse complete(String runId, CompletionRequest request) {
    CompletionResponse duplicate = completed.get(request.eventId());
    if (duplicate != null) return duplicate;
    var assignment = state.require(request.trainingJobId(), request.attempt());
    if (assignment.runId() == null || !assignment.runId().equals(runId))
      throw new IllegalStateException("완료 콜백의 runId가 활성 학습과 일치하지 않습니다.");
    long eventSequence =
        completionSequences.computeIfAbsent(
            request.eventId(), ignored -> assignment.lastForwardedSequence() + 2);
    events.trainingFinished(
        request.trainingJobId(), request.attempt(), assignment.leaseToken(),
        request.eventId() + "-finished", eventSequence, request.result().sizeBytes(),
        request.result().sha256(), request.modelCompletedAt());
    Path checkpoint =
        worker.storageRoot().resolve(request.result().checkpointStorageKey()).normalize();
    var uploaded =
        checkpoints.uploadAndComplete(
            request.trainingJobId(), request.attempt(), assignment.leaseToken(),
            request.eventId() + "-upload", request.eventId() + "-complete",
            new LocalCheckpoint(checkpoint, request.modelCompletedAt(), request.result().metadata()));
    CompletionResponse response =
        new CompletionResponse(
            uploaded.skillVersionId(), uploaded.version(), uploaded.completion().status());
    completed.put(request.eventId(), response);
    state.release(assignment.gpuIndex());
    return response;
  }

  public record CompletionResponse(java.util.UUID skillVersionId, int version, String status) {}
}
