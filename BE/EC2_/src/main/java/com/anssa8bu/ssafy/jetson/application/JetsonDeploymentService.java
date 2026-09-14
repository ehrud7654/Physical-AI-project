package com.anssa8bu.ssafy.jetson.application;

import static com.anssa8bu.ssafy.jetson.dto.JetsonDeploymentModels.*;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.DeploymentResponse;
import com.anssa8bu.ssafy.deployment.entity.*;
import com.anssa8bu.ssafy.deployment.repository.*;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.task.repository.TaskStepRepository;
import java.net.URL;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class JetsonDeploymentService {
  private static final Set<DeploymentStatus> PENDING =
      EnumSet.of(
          DeploymentStatus.PENDING,
          DeploymentStatus.DOWNLOADING,
          DeploymentStatus.VERIFYING,
          DeploymentStatus.APPLYING);
  private final JetsonIdentityService identity;
  private final DeploymentRepository deployments;
  private final DeploymentItemRepository items;
  private final DeploymentReportRepository reports;
  private final SkillVersionRepository skills;
  private final TaskStepRepository steps;
  private final S3Presigner presigner;
  private final Clock clock;
  private final Duration urlTtl;

  public JetsonDeploymentService(
      JetsonIdentityService identity,
      DeploymentRepository deployments,
      DeploymentItemRepository items,
      DeploymentReportRepository reports,
      SkillVersionRepository skills,
      TaskStepRepository steps,
      S3Presigner presigner,
      Clock clock,
      @Value("${app.jetson.checkpoint-url-ttl:15m}") Duration urlTtl) {
    this.identity = identity;
    this.deployments = deployments;
    this.items = items;
    this.reports = reports;
    this.skills = skills;
    this.steps = steps;
    this.presigner = presigner;
    this.clock = clock;
    this.urlTtl = urlTtl;
  }

  @Transactional(readOnly = true)
  public Optional<Manifest> pending(Jwt jwt) {
    Robot robot = identity.requireRobot(jwt);
    return deployments
        .findFirstByRobotIdAndStatusInOrderByCreatedAtAsc(robot.getId(), PENDING)
        .map(deployment -> manifest(robot, deployment));
  }

  @Transactional(readOnly = true)
  public Manifest manifest(Jwt jwt, UUID deploymentId) {
    Robot robot = identity.requireRobot(jwt);
    Deployment deployment = owned(robot, deploymentId);
    if (deployment.getStatus() == DeploymentStatus.CANCELED
        || deployment.getStatus() == DeploymentStatus.FAILED) {
      throw new BusinessConflictException("STATE_CONFLICT", "종료된 배포의 manifest는 재발급할 수 없습니다.");
    }
    return manifest(robot, deployment);
  }

  @Transactional
  public DeploymentResponse report(Jwt jwt, UUID deploymentId, ReportRequest request) {
    Robot robot = identity.requireRobot(jwt);
    Deployment deployment = owned(robot, deploymentId);
    Optional<DeploymentReport> duplicate =
        reports.findByDeploymentIdAndReportId(deploymentId, request.reportId());
    if (duplicate.isPresent()) {
      if (duplicate.get().getSequence() != request.sequence()) {
        throw new BusinessConflictException("REPORT_CONFLICT", "동일 reportId의 sequence가 다릅니다.");
      }
      return response(deployment);
    }
    if (reports.findByDeploymentIdAndSequence(deploymentId, request.sequence()).isPresent()
        || request.sequence() <= deployment.getLastReportSequence()) {
      throw new BusinessConflictException("REPORT_OUT_OF_ORDER", "배포 보고 sequence가 중복되거나 역순입니다.");
    }
    String errorCode = request.error() == null ? null : request.error().code();
    String errorMessage = request.error() == null ? null : request.error().message();
    Instant now = clock.instant();
    try {
      deployment.acceptReport(
          request.sequence(),
          request.status(),
          request.progressPercent(),
          errorCode,
          errorMessage,
          now);
    } catch (IllegalArgumentException exception) {
      throw new BusinessConflictException("INVALID_DEPLOYMENT_REPORT", exception.getMessage());
    }
    reports.save(
        new DeploymentReport(
            UUID.randomUUID(),
            deploymentId,
            request.reportId(),
            request.sequence(),
            request.status(),
            request.progressPercent(),
            request.items(),
            request.error() == null ? null : Map.of("code", errorCode, "message", errorMessage),
            now));
    if (request.status() == DeploymentStatus.SUCCEEDED) robot.activateDeployment(deploymentId);
    return response(deployment);
  }

  private Manifest manifest(Robot robot, Deployment deployment) {
    Instant generatedAt = clock.instant();
    Instant expiresAt = generatedAt.plus(urlTtl);
    List<Model> models =
        items.findAllByDeploymentId(deployment.getId()).stream()
            .map(
                item ->
                    skills
                        .findById(item.getSkillVersionId())
                        .orElseThrow(() -> new ResourceNotFoundException("SkillVersion")))
            .map(version -> model(version, expiresAt))
            .toList();
    List<com.anssa8bu.ssafy.jetson.dto.JetsonCommand.Step> workflow =
        steps.findAllByTaskVersionIdOrderByStepOrder(deployment.getTaskVersionId()).stream()
            .map(
                step ->
                    new com.anssa8bu.ssafy.jetson.dto.JetsonCommand.Step(
                        step.getId(),
                        step.getParentStepId(),
                        step.getStepOrder(),
                        step.getStepType().name(),
                        step.getSkillVersionId(),
                        step.getTimeoutSeconds(),
                        step.getRepeatCount(),
                        step.getParameters()))
            .toList();
    return new Manifest(
        deployment.getId(),
        robot.getId(),
        deployment.getTaskVersionId(),
        models,
        workflow,
        generatedAt);
  }

  private Model model(SkillVersion version, Instant expiresAt) {
    URL url =
        presigner
            .presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(urlTtl)
                    .getObjectRequest(
                        GetObjectRequest.builder()
                            .bucket(version.getCheckpointBucket())
                            .key(version.getCheckpointKey())
                            .build())
                    .build())
            .url();
    return new Model(
        version.getId(),
        url.toString(),
        expiresAt,
        version.getCheckpointBytes(),
        version.getCheckpointSha256(),
        version.getArchitecture(),
        version.getFramework(),
        version.getFrameworkVersion(),
        version.getContractVersion(),
        version.getActionSpace(),
        version.getParameterCount(),
        version.getControlRateHz(),
        version.getCameraNames(),
        version.getInputSchema(),
        version.getOutputSchema());
  }

  private Deployment owned(Robot robot, UUID deploymentId) {
    return deployments
        .findByIdAndRobotId(deploymentId, robot.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Deployment"));
  }

  private DeploymentResponse response(Deployment deployment) {
    return new DeploymentResponse(
        deployment.getId(),
        deployment.getRobotId(),
        deployment.getTaskVersionId(),
        deployment.getStatus(),
        deployment.getProgressPercent(),
        items.findAllByDeploymentId(deployment.getId()).stream()
            .map(
                item ->
                    new com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.ItemResponse(
                        item.getId(),
                        item.getSkillVersionId(),
                        item.isGatePassed(),
                        item.getStatus(),
                        item.getDownloadedAt(),
                        item.getVerifiedAt(),
                        item.getErrorCode() == null
                            ? null
                            : new com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.ErrorInfo(
                                item.getErrorCode(), item.getErrorMessage())))
            .toList(),
        deployment.getErrorCode() == null
            ? null
            : new com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.ErrorInfo(
                deployment.getErrorCode(), deployment.getErrorMessage()),
        deployment.getCreatedAt(),
        deployment.getUpdatedAt(),
        deployment.getAppliedAt());
  }
}
