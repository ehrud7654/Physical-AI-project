package com.anssa8bu.ssafy.task.repository;

import com.anssa8bu.ssafy.task.entity.TaskVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskVersionRepository extends JpaRepository<TaskVersion, UUID> {
  List<TaskVersion> findAllByTaskIdOrderByVersionDesc(UUID taskId);

  Optional<TaskVersion> findByIdAndTaskId(UUID id, UUID taskId);

  Optional<TaskVersion> findFirstByTaskIdOrderByVersionDesc(UUID taskId);
}
