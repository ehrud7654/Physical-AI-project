package com.anssa8bu.ssafy_gpu.training.application;

import static com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.*;

import com.anssa8bu.ssafy_gpu.training.client.TrainingJobClient;
import com.anssa8bu.ssafy_gpu.training.persistence.TrainingEventJournal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TrainingEventService {
  private final TrainingJobClient client;
  private final TrainingEventJournal journal;

  public TrainingEventService(TrainingJobClient client, TrainingEventJournal journal) {
    this.client = client;
    this.journal = journal;
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
    EventRequest request =
        new EventRequest(
            attempt, leaseToken, eventId, sequence, type.name(), occurredAt, Map.copyOf(payload));
    var staged = journal.stage(jobId, request);
    if (staged.response() != null) return staged.response();
    try {
      EventResponse response = client.event(jobId, request);
      journal.delivered(eventId, response);
      return response;
    } catch (RuntimeException exception) {
      journal.failed(eventId);
      throw exception;
    }
  }

  public EventResponse retry(TrainingEventJournal.Entry entry) {
    try {
      EventResponse response = client.event(entry.jobId(), entry.request());
      journal.delivered(entry.request().eventId(), response);
      return response;
    } catch (RuntimeException exception) {
      journal.failed(entry.request().eventId());
      throw exception;
    }
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
    var previous = journal.find(eventId);
    if (previous != null) occurredAt = previous.request().occurredAt();
    return send(
        jobId, attempt, token, eventId, sequence, TrainingEventType.CANCELED, occurredAt, Map.of());
  }
}
