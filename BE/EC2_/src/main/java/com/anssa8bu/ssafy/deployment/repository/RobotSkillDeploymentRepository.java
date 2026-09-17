package com.anssa8bu.ssafy.deployment.repository;

import com.anssa8bu.ssafy.deployment.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotSkillDeploymentRepository extends JpaRepository<RobotSkillDeployment, UUID> {
  List<RobotSkillDeployment> findAllByRobotIdAndStatusOrderByDeployedAtDesc(
      UUID robotId, RobotSkillDeploymentStatus status);
}
