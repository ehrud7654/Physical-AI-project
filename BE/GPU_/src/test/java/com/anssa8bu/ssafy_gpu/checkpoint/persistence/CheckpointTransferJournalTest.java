package com.anssa8bu.ssafy_gpu.checkpoint.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.anssa8bu.ssafy_gpu.checkpoint.dto.CheckpointModels.*;
import com.anssa8bu.ssafy_gpu.training.persistence.TrainingEventJournalTest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CheckpointTransferJournalTest {
  @TempDir Path root;

  @Test
  void resumesEveryCheckpointTransferBoundary() {
    var journal =
        new CheckpointTransferJournal(
            TrainingEventJournalTest.properties(root),
            JsonMapper.builder().findAndAddModules().build());
    UUID jobId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UploadSessionRequest request =
        new UploadSessionRequest(
            1,
            "lease",
            "model.ckpt",
            Instant.EPOCH,
            "application/octet-stream",
            10,
            "a".repeat(64),
            null);
    journal.stage(jobId, 1, "upload-key", "complete-key", root.resolve("model.ckpt"), request);
    assertThat(journal.isFinalizationPending(jobId, 1)).isTrue();

    journal.session(
        jobId,
        1,
        new UploadSessionResponse(
            versionId, 2, "PUT", "https://example.invalid", Instant.MAX, Map.of()));
    journal.uploaded(jobId, 1);
    assertThat(journal.find(jobId, 1).s3PutCompleted()).isTrue();

    journal.completed(jobId, 1, new CompleteResponse(versionId, "VALIDATING"));
    assertThat(journal.isFinalizationPending(jobId, 1)).isFalse();
    assertThat(journal.find(jobId, 1).completeResponse().skillVersionId()).isEqualTo(versionId);
  }
}
