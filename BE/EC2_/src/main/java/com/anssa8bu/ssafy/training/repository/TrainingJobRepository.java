package com.anssa8bu.ssafy.training.repository;

import com.anssa8bu.ssafy.training.entity.TrainingJob;
import com.anssa8bu.ssafy.training.entity.TrainingJobStatus;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingJobRepository extends JpaRepository<TrainingJob, UUID> {
  List<TrainingJob> findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);

  Optional<TrainingJob> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

  Optional<TrainingJob> findByIdAndOwnerId(UUID id, UUID ownerId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from TrainingJob j where j.id = :id")
  Optional<TrainingJob> findByIdForUpdate(@Param("id") UUID id);

  @Query(value = "SELECT id FROM training_jobs WHERE deleted_at IS NULL AND status IN ('QUEUED','CREATED') ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1", nativeQuery = true)
  List<UUID> lockNextClaimableId();

  Optional<TrainingJob> findFirstByAssignedWorkerIdAndLeaseExpiresAtAfterAndStatusInOrderByCreatedAt(
      String workerId, java.time.Instant now, Collection<TrainingJobStatus> statuses);
}
