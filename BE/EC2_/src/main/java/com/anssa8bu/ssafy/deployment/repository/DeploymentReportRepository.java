package com.anssa8bu.ssafy.deployment.repository;

import com.anssa8bu.ssafy.deployment.entity.DeploymentReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentReportRepository extends JpaRepository<DeploymentReport, UUID> {
  Optional<DeploymentReport> findByDeploymentIdAndReportId(UUID deploymentId, String reportId);

  Optional<DeploymentReport> findByDeploymentIdAndSequence(UUID deploymentId, long sequence);
}
