package com.anssa8bu.ssafy_gpu.training.application;

import static com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.*;

import com.anssa8bu.ssafy_gpu.training.client.TrainingJobClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TrainingEventService {
  private final TrainingJobClient client;

  public TrainingEventService(TrainingJobClient client) {
    this.client = client;
  }

  public EventResponse send(
      UUID jobId,
      int attempt,
      String leaseToken,
      String eventId,
      long sequence,
      TrainingEventType type,
      Instant occurredAt,
      Map<String, Object> payload) {
    return client.event(
        jobId,
        new EventRequest(
            attempt, leaseToken, eventId, sequence, type.name(), occurredAt, Map.copyOf(payload)));
  }

  public EventResponse started(
      UUID jobId, int attempt, String token, String eventId, long sequence, Instant occurredAt) {
    return send(
        jobId, attempt, token, eventId, sequence, TrainingEventType.STARTED, occurredAt, Map.of());
  }

  public EventResponse failed(
      UUID jobId,
      int attempt,
      String token,
      String eventId,
      long sequence,
      String code,
      String message,
      Instant occurredAt) {
    return send(
        jobId,
        attempt,
        token,
        eventId,
        sequence,
        TrainingEventType.FAILED,
        occurredAt,
        Map.of("code", code, "message", message));
  }

  public EventResponse progress(
      UUID jobId,
      int attempt,
      String token,
      String eventId,
      long sequence,
      String stage,
      BigDecimal percent,
      Integer epoch,
      Integer totalEpochs,
      Map<String, Object> metrics,
      Instant occurredAt) {
    return send(
        jobId,
        attempt,
        token,
        eventId,
        sequence,
        TrainingEventType.PROGRESS,
        occurredAt,
        progressPayload(stage, percent, epoch, totalEpochs, metrics));
  }

  public EventResponse trainingFinished(
      UUID jobId,
      int attempt,
      String token,
      String eventId,
      long sequence,
      long checkpointSizeBytes,
      String checkpointSha256,
      Instant occurredAt) {
    return send(
        jobId,
        attempt,
        token,
        eventId,
        sequence,
        TrainingEventType.TRAINING_FINISHED,
        occurredAt,
        Map.of(
            "checkpointSizeBytes", checkpointSizeBytes,
            "checkpointSha256", checkpointSha256));
  }

  public EventResponse canceled(
      UUID jobId, int attempt, String token, String eventId, long sequence, Instant occurredAt) {
    return send(
        jobId, attempt, token, eventId, sequence, TrainingEventType.CANCELED, occurredAt, Map.of());
  }
}
