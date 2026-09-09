package com.anssa8bu.ssafy.task.repository;

import com.anssa8bu.ssafy.task.entity.TaskStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStepRepository extends JpaRepository<TaskStep, UUID> {
  List<TaskStep> findAllByTaskVersionIdOrderByStepOrder(UUID taskVersionId);
  long countByTaskVersionId(UUID taskVersionId);
}
