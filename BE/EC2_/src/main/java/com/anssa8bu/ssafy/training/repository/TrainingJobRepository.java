package com.anssa8bu.ssafy.training.repository;

import com.anssa8bu.ssafy.training.entity.TrainingJob;
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
}
