package com.anssa8bu.ssafy.execution.repository;

import com.anssa8bu.ssafy.execution.entity.RobotExecution;
import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotExecutionRepository extends JpaRepository<RobotExecution, UUID> {
  Optional<RobotExecution> findByIdAndOwnerId(UUID id, UUID ownerId);

  boolean existsByRobotIdAndStatusIn(UUID robotId, Collection<RobotExecutionStatus> statuses);
}
