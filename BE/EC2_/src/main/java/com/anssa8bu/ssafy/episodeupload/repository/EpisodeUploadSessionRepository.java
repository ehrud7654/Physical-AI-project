package com.anssa8bu.ssafy.episodeupload.repository;

import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EpisodeUploadSessionRepository extends JpaRepository<EpisodeUploadSession, UUID> {
  Optional<EpisodeUploadSession> findByOwnerIdAndUmiDeviceIdAndSessionId(UUID ownerId, UUID deviceId, UUID sessionId);
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  Optional<EpisodeUploadSession> findForUpdateById(UUID id);
}
