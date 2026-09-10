package com.anssa8bu.ssafy.deployment.repository;
import com.anssa8bu.ssafy.deployment.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {
  List<Deployment> findAllByRobotIdOrderByCreatedAtDesc(UUID robotId);
  Optional<Deployment> findByIdAndRobotId(UUID id, UUID robotId);
  boolean existsByRobotIdAndStatusIn(UUID robotId, Collection<DeploymentStatus> statuses);
}
