package com.anssa8bu.ssafy.deployment.repository;

import com.anssa8bu.ssafy.deployment.entity.DeploymentItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentItemRepository extends JpaRepository<DeploymentItem, UUID> {
  List<DeploymentItem> findAllByDeploymentId(UUID deploymentId);
}
