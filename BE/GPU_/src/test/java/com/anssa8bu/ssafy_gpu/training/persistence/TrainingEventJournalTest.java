package com.anssa8bu.ssafy_gpu.training.persistence;

import static org.assertj.core.api.Assertions.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

public class TrainingEventJournalTest {
  @TempDir Path root;

  @Test
  void persistsPayloadHashDeliveryAndRetryState() {
    var journal = new TrainingEventJournal(properties(root), mapper());
    UUID jobId = UUID.randomUUID();
    EventRequest request =
        new EventRequest(
            1,
            "lease",
            "event-1",
            1,
            "PROGRESS",
            Instant.parse("2026-09-18T00:00:00Z"),
            Map.of("percent", new BigDecimal("10")));

    journal.stage(jobId, request);
    journal.failed(request.eventId());
    assertThat(journal.find(request.eventId()).attempts()).isEqualTo(1);
    assertThat(journal.find(request.eventId()).payloadHash()).hasSize(64);

    EventResponse response = new EventResponse(true, TrainingJobStatus.RUNNING, 1);
    journal.delivered(request.eventId(), response);
    assertThat(journal.find(request.eventId()).response()).isEqualTo(response);
    assertThat(journal.retryable(Instant.now().plus(Duration.ofDays(1)))).isEmpty();
  }

  @Test
  void rejectsSameEventIdWithDifferentPayload() {
    var journal = new TrainingEventJournal(properties(root), mapper());
    UUID jobId = UUID.randomUUID();
    journal.stage(
        jobId, new EventRequest(1, "lease", "event-1", 1, "STARTED", Instant.EPOCH, Map.of()));
    assertThatThrownBy(
            () ->
                journal.stage(
                    jobId,
                    new EventRequest(1, "lease", "event-1", 2, "STARTED", Instant.EPOCH, Map.of())))
        .isInstanceOf(IllegalStateException.class);
  }

  private static tools.jackson.databind.ObjectMapper mapper() {
    return JsonMapper.builder().findAndAddModules().build();
  }

  public static WorkerProperties properties(Path root) {
    return new WorkerProperties(
        "worker",
        "node",
        "name",
        root,
        1,
        Duration.ofSeconds(15),
        true,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        1024,
        "test");
  }
}
