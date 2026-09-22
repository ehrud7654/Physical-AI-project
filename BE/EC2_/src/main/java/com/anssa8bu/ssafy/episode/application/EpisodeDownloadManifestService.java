package com.anssa8bu.ssafy.episode.application;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.*;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.storage.PresignedDownloadService;
import com.anssa8bu.ssafy.episode.entity.*;
import com.anssa8bu.ssafy.episode.repository.*;
import com.anssa8bu.ssafy.episodeupload.storage.EpisodeStorageProperties;
import com.anssa8bu.ssafy.training.repository.TrainingJobEpisodeRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EpisodeDownloadManifestService {
  private final TrainingJobEpisodeRepository inputs;
  private final EpisodeRepository episodes;
  private final EpisodeArchiveRepository archives;
  private final PresignedDownloadService downloads;
  private final EpisodeStorageProperties properties;
  public EpisodeDownloadManifestService(TrainingJobEpisodeRepository inputs, EpisodeRepository episodes,
      EpisodeArchiveRepository archives, PresignedDownloadService downloads,
      EpisodeStorageProperties properties) {
    this.inputs=inputs; this.episodes=episodes; this.archives=archives;
    this.downloads=downloads; this.properties=properties;
  }
  public List<EpisodeData> create(UUID jobId) {
    return inputs.findAllByIdTrainingJobIdOrderByInputOrder(jobId).stream().map(input -> {
      UUID id=input.getId().episodeId();
      Episode episode=episodes.findById(id).orElseThrow();
      EpisodeArchive archive=archives.findByEpisodeId(id)
          .filter(a -> a.getStatus()==EpisodeArchiveStatus.AVAILABLE)
          .orElseThrow(() -> new BusinessConflictException("EPISODE_NOT_AVAILABLE", "학습 입력 파일을 사용할 수 없습니다."));
      var signed=downloads.issue(properties.bucket(), archive.getStorageKey(), properties.presignTtl());
      return new EpisodeData(id, input.getInputRole(), episode.getSessionId(),
          episode.getQualityStatus().name(),
          episode.getContractVersion(),
          new ArchiveData(signed.url(), signed.expiresAt(), archive.getContentType(),
              archive.getSizeBytes(), archive.getSha256()));
    }).toList();
  }
}
