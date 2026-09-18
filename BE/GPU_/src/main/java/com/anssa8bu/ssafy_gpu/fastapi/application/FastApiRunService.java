package com.anssa8bu.ssafy_gpu.fastapi.application;

import static com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.*;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.fastapi.client.FastApiTrainingClient;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
import com.anssa8bu.ssafy_gpu.training.storage.BaseModelDownloader.LocalBaseModel;
import com.anssa8bu.ssafy_gpu.training.storage.EpisodeArchiveDownloader.LocalEpisode;
import com.anssa8bu.ssafy_gpu.worker.WorkerRuntimeState;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FastApiRunService {
  private final FastApiTrainingClient client;
  private final WorkerProperties worker;
  private final WorkerRuntimeState state;

  public FastApiRunService(
      FastApiTrainingClient client, WorkerProperties worker, WorkerRuntimeState state) {
    this.client = client;
    this.worker = worker;
    this.state = state;
  }

  public CreateRunResponse start(
      ClaimResponse claim, int gpuIndex, List<LocalEpisode> episodes, LocalBaseModel baseModel) {
    BaseModelInput base =
        baseModel == null
            ? null
            : new BaseModelInput(
                baseModel.skillVersionId(), baseModel.version(), baseModel.storageKey(),
                baseModel.sha256(), claim.job().baseModel().architecture(),
                claim.job().baseModel().framework(), claim.job().baseModel().frameworkVersion(),
                claim.job().baseModel().contractVersion());
    List<EpisodeInput> inputs =
        episodes.stream()
            .map(value -> new EpisodeInput(
                value.inputOrder(), value.episodeId(), value.storageKey(), value.sha256(),
                value.contractVersion()))
            .toList();
    CreateRunResponse response =
        client.create(
            new CreateRunRequest(
                claim.job().id(), claim.attempt(), worker.workerId(), state.storageNodeId(),
                gpuIndex, claim.job().trainingMode().name(), base,
                claim.job().inputSnapshotSha256(), inputs, claim.leaseExpiresAt()));
    state.bindRun(gpuIndex, response.runId());
    return response;
  }

  public RunResponse get(String runId) { return client.get(runId); }
  public void cancel(String runId) { client.cancel(runId, "EC2_CANCEL_REQUESTED"); }
  public void renewLease(String runId, java.time.Instant expiresAt) {
    client.renewLease(runId, expiresAt);
  }
}
