package com.anssa8bu.ssafy_gpu.training.cleanup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.anssa8bu.ssafy_gpu.checkpoint.persistence.CheckpointTransferJournal;
import com.anssa8bu.ssafy_gpu.training.persistence.TrainingEventJournalTest;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TrainingArtifactCleanupServiceTest {
  @TempDir Path root;
  private WorkerRuntimeState state;
  private CheckpointTransferJournal checkpoints;
  private TrainingArtifactCleanupService cleanup;

  @BeforeEach
  void setUp() {
    var properties = TrainingEventJournalTest.properties(root);
    state = new WorkerRuntimeState(JsonMapper.builder().findAndAddModules().build(), properties);
    checkpoints = mock(CheckpointTransferJournal.class);
    cleanup =
        new TrainingArtifactCleanupService(
            properties,
            state,
            new TrainingArtifactCleanupProperties(true, Duration.ZERO, Duration.ofMinutes(1)),
            checkpoints,
            new SimpleMeterRegistry());
  }

  @Test
  void doesNotDeleteActiveTrainingDirectory() throws Exception {
    UUID jobId = UUID.randomUUID();
    Path file = createArtifact(jobId, 1);
    state.activate(
        new WorkerRuntimeState.ActiveTraining(0, jobId, 1, "lease", Instant.now().plusSeconds(60)));

    cleanup.cleanup(jobId, 1);

    assertThat(file).exists();
  }

  @Test
  void doesNotDeleteCheckpointPendingDirectory() throws Exception {
    UUID jobId = UUID.randomUUID();
    Path file = createArtifact(jobId, 1);
    when(checkpoints.isFinalizationPending(jobId, 1)).thenReturn(true);

    cleanup.cleanup(jobId, 1);

    assertThat(file).exists();
  }

  @Test
  void deletesTerminalTrainingArtifacts() throws Exception {
    UUID jobId = UUID.randomUUID();
    Path file = createArtifact(jobId, 1);

    cleanup.cleanup(jobId, 1);

    assertThat(file).doesNotExist();
  }

  @Test
  void refusesSymbolicLinkAttemptDirectory() throws Exception {
    UUID jobId = UUID.randomUUID();
    Path outside = Files.createDirectories(root.resolve("outside"));
    Path link = root.resolve("training-inputs").resolve(jobId.toString()).resolve("1");
    Files.createDirectories(link.getParent());
    try {
      Files.createSymbolicLink(link, outside);
    } catch (UnsupportedOperationException | FileSystemException exception) {
      Assumptions.abort("이 환경에서는 symbolic link 테스트를 실행할 수 없습니다.");
    }

    assertThatThrownBy(() -> cleanup.cleanup(jobId, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(outside).exists();
  }

  private Path createArtifact(UUID jobId, int attempt) throws Exception {
    Path directory =
        root.resolve("training-inputs")
            .resolve(jobId.toString())
            .resolve(Integer.toString(attempt));
    Files.createDirectories(directory);
    return Files.writeString(directory.resolve("episode.zip"), "data");
  }
}
