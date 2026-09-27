package com.anssa8bu.ssafy.task.repository;

import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import com.anssa8bu.ssafy.task.entity.Task;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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

  Optional<Task> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

  List<Task> findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID ownerId);

  List<Task>
      findAllByOwnerIdAndDeletedAtIsNullAndDisplayNameContainingIgnoreCaseOrderByUpdatedAtDesc(
          UUID ownerId, String q);

  @Query(
      "select (count(r) > 0) from Robot r where r.currentDeploymentId in "
          + "(select d.id from Deployment d where d.taskVersionId in "
          + "(select tv.id from TaskVersion tv where tv.taskId = :taskId))")
  boolean hasActiveDeployment(@Param("taskId") UUID taskId);

  @Query(
      "select (count(e) > 0) from RobotExecution e where e.taskVersionId in "
          + "(select tv.id from TaskVersion tv where tv.taskId = :taskId) and e.status in :statuses")
  boolean hasActiveExecution(
      @Param("taskId") UUID taskId, @Param("statuses") Collection<RobotExecutionStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Task t where t.id = :id and t.ownerId = :ownerId and t.deletedAt is null")
  Optional<Task> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
