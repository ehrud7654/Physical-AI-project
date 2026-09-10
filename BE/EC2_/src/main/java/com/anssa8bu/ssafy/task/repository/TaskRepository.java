package com.anssa8bu.ssafy.task.repository;

import com.anssa8bu.ssafy.task.entity.Task;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {
  boolean existsByOwnerIdAndTaskKey(UUID ownerId, String taskKey);
  Optional<Task> findByIdAndOwnerId(UUID id, UUID ownerId);
  List<Task> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
  List<Task> findAllByOwnerIdAndDisplayNameContainingIgnoreCaseOrderByUpdatedAtDesc(UUID ownerId, String q);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Task t where t.id = :id and t.ownerId = :ownerId")
  Optional<Task> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
