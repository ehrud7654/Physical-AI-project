package com.anssa8bu.ssafy_gpu.training.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.anssa8bu.ssafy_gpu.common.config.WorkerProperties;
import com.anssa8bu.ssafy_gpu.common.file.FileHashCalculator;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ArchiveData;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.ClaimResponse;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.EpisodeData;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.JobData;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.TrainingEpisodeRole;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.TrainingInputMode;
import com.anssa8bu.ssafy_gpu.training.dto.TrainingJobModels.TrainingMode;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpisodeArchiveDownloaderTest {
  @TempDir Path root;

  @Test
  void reusesVerifiedEpisodeFromOwnerCacheWithoutHttpRequest() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID episodeId = UUID.randomUUID();
    byte[] content = "cached-episode".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Path cached =
        root.resolve("training-inputs").resolve(ownerId.toString()).resolve(episodeId + ".zip");
    Files.createDirectories(cached.getParent());
    Files.write(cached, content);
    var hashes = new FileHashCalculator();
    var client = mock(HttpClient.class);
    var downloader = new EpisodeArchiveDownloader(client, hashes, properties());
    var episode =
        new EpisodeData(
            episodeId,
            "session-1",
            TrainingEpisodeRole.DEMONSTRATION,
            "VALID",
            "umi-v1",
            new ArchiveData(
                "https://expired.invalid/object",
                Instant.EPOCH,
                "application/zip",
                content.length,
                hashes.sha256(cached)));
    var job =
        new JobData(
            UUID.randomUUID(),
            ownerId,
            "test-skill",
            TrainingInputMode.EXISTING_EPISODES,
            TrainingMode.FROM_SCRATCH,
            null,
            Map.of(),
            "snapshot",
            List.of(episode));

    var result =
        downloader.downloadAll(
            new ClaimResponse(job, 2, "lease", Instant.now().plusSeconds(60), 10));

    assertThat(result)
        .singleElement()
        .satisfies(value -> assertThat(value.path()).isEqualTo(cached));
    assertThat(result.getFirst().storageKey())
        .isEqualTo("training-inputs/" + ownerId + "/" + episodeId + ".zip");
    verifyNoInteractions(client);
  }

  private WorkerProperties properties() {
    return new WorkerProperties(
        "worker",
        "node",
        "name",
        root,
        1,
        Duration.ofSeconds(30),
        true,
        Duration.ZERO,
        Duration.ofSeconds(5),
        1024,
        "test");
  }
}
