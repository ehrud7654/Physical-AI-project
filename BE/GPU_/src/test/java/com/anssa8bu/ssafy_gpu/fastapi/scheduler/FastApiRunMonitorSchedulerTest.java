package com.anssa8bu.ssafy_gpu.fastapi.scheduler;

import static org.mockito.Mockito.*;

import com.anssa8bu.ssafy_gpu.fastapi.application.FastApiRunService;
import com.anssa8bu.ssafy_gpu.fastapi.application.TrainingCompletionOrchestrator;
import com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.RunError;
import com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.RunResponse;
import com.anssa8bu.ssafy_gpu.training.application.TrainingEventService;
import com.anssa8bu.ssafy_gpu.training.cleanup.TrainingArtifactCleanupService;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState.ActiveTraining;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FastApiRunMonitorSchedulerTest {

  @Test
  void forwardsFailedRunEvenWhenFastApiSequenceEqualsStartedEventSequence() {
    FastApiRunService fastApi = mock(FastApiRunService.class);
    TrainingEventService events = mock(TrainingEventService.class);
    WorkerRuntimeState state = mock(WorkerRuntimeState.class);
    TrainingArtifactCleanupService cleanup = mock(TrainingArtifactCleanupService.class);
    TrainingCompletionOrchestrator completion = mock(TrainingCompletionOrchestrator.class);
    UUID jobId = UUID.randomUUID();
    ActiveTraining assignment =
        new ActiveTraining(0, jobId, 1, "lease", Instant.now().plusSeconds(60), "run:1", 1L);
    Instant failedAt = Instant.now();
    RunResponse failed =
        new RunResponse(
            "run:1",
            jobId,
            1,
            "FAILED",
            1L,
            "FAILED",
            BigDecimal.ZERO,
            null,
            null,
            failedAt,
            failedAt,
            null,
            null,
            new RunError("TRAINING_PROCESS_FAILED", "trainer missing"));
    when(state.activeTrainingAssignments()).thenReturn(List.of(assignment));
    when(fastApi.get("run:1")).thenReturn(failed);

    new FastApiRunMonitorScheduler(fastApi, events, state, cleanup, completion).monitor();

    verify(events)
        .failed(
            jobId,
            1,
            "lease",
            "fastapi-run:1-1",
            2L,
            "TRAINING_PROCESS_FAILED",
            "trainer missing",
            failedAt);
    verify(state).release(0);
    verify(cleanup).cleanup(jobId, 1);
  }
}
