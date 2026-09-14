package com.anssa8bu.ssafy.episodeupload.repository;

import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EpisodeUploadSessionRepository extends JpaRepository<EpisodeUploadSession, UUID> {
  Optional<EpisodeUploadSession> findByOwnerIdAndUmiDeviceIdAndSessionId(UUID ownerId, UUID deviceId, UUID sessionId);
  Optional<EpisodeUploadSession> findByTrainingJobIdAndOwnerIdAndUmiDeviceIdAndSessionId(
      UUID trainingJobId, UUID ownerId, UUID deviceId, UUID sessionId);
  Optional<EpisodeUploadSession> findByIdAndTrainingJobIdAndOwnerIdAndUmiDeviceId(UUID id, UUID jobId, UUID ownerId, UUID deviceId);
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  Optional<EpisodeUploadSession> findForUpdateById(UUID id);
}
