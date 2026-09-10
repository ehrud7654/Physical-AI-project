package com.anssa8bu.ssafy.deployment.application;
import static com.anssa8bu.ssafy.deployment.dto.DeploymentApiModels.*;
import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.deployment.entity.*;
import com.anssa8bu.ssafy.deployment.repository.*;
import com.anssa8bu.ssafy.model.entity.*;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import com.anssa8bu.ssafy.task.entity.*;
import com.anssa8bu.ssafy.task.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentService {
  private static final Set<DeploymentStatus> ACTIVE = EnumSet.of(DeploymentStatus.PENDING,
      DeploymentStatus.DOWNLOADING, DeploymentStatus.VERIFYING, DeploymentStatus.APPLYING);
  private final DeploymentRepository deployments; private final DeploymentItemRepository items;
  private final RobotRepository robots; private final TaskVersionRepository versions;
  private final TaskRepository tasks; private final TaskStepRepository steps;
  private final SkillVersionRepository skills;
  private final IdempotencyGuard idempotency;
  public DeploymentService(DeploymentRepository d, DeploymentItemRepository i, RobotRepository r,
      TaskVersionRepository v, TaskRepository t, TaskStepRepository s, SkillVersionRepository sv,
      IdempotencyGuard idempotency) {
    deployments=d;items=i;robots=r;versions=v;tasks=t;steps=s;skills=sv;
    this.idempotency = idempotency;
  }
  @Transactional
  public DeploymentResponse create(UUID ownerId, UUID robotId, UUID taskVersionId,
      String idempotencyKey) {
    String path = "/api/v1/robots/" + robotId + "/deployments";
    var claim = idempotency.claim(ownerId, "POST", path, idempotencyKey,
        Map.of("taskVersionId", taskVersionId));
    if (claim.existingResourceId() != null) {
      return get(ownerId, robotId, claim.existingResourceId());
    }
    Robot robot = ownedRobot(ownerId, robotId);
    TaskVersion version = versions.findById(taskVersionId)
        .orElseThrow(() -> new ResourceNotFoundException("TaskVersion"));
    tasks.findByIdAndOwnerId(version.getTaskId(), ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("TaskVersion"));
    if (deployments.existsByRobotIdAndStatusIn(robotId, ACTIVE))
      throw new BusinessConflictException("ROBOT_BUSY", "진행 중인 배포가 있습니다.");
    List<TaskStep> taskSteps = steps.findAllByTaskVersionIdOrderByStepOrder(taskVersionId);
    if (taskSteps.isEmpty()) throw new BusinessConflictException("EMPTY_TASK", "배포할 단계가 없습니다.");
    Set<UUID> referencedSkillIds = taskSteps.stream()
        .filter(step -> step.getStepType() == TaskStepType.SKILL)
        .map(TaskStep::getSkillVersionId).collect(Collectors.toSet());
    List<SkillVersion> skillVersions = skills.findAllByIdIn(
        referencedSkillIds);
    if (skillVersions.size() != referencedSkillIds.size()
        || skillVersions.stream().anyMatch(s -> s.getStatus()!=SkillVersionStatus.READY || !s.isGatePassed()))
      throw new BusinessConflictException("SKILL_VERSION_GATE_FAILED", "게이트를 통과하지 못한 스킬 버전이 있습니다.");
    if (skillVersions.stream().anyMatch(s -> !s.getRobotProfileId().equals(robot.getRobotProfileId())))
      throw new BusinessConflictException("MODEL_INCOMPATIBLE", "로봇 프로파일과 호환되지 않습니다.");
    Deployment deployment = deployments.save(new Deployment(UUID.randomUUID(), robotId, taskVersionId));
    items.saveAll(referencedSkillIds.stream()
        .map(id -> new DeploymentItem(UUID.randomUUID(), deployment.getId(), id)).toList());
    idempotency.complete(ownerId, "POST", path, claim, 201, "DEPLOYMENT", deployment.getId());
    return map(deployment);
  }
  @Transactional(readOnly=true) public List<DeploymentResponse> list(UUID owner, UUID robot) {
    ownedRobot(owner,robot); return deployments.findAllByRobotIdOrderByCreatedAtDesc(robot).stream().map(this::map).toList();
  }
  @Transactional(readOnly=true) public DeploymentResponse get(UUID owner, UUID robot, UUID id) {
    ownedRobot(owner,robot); return map(ownedDeployment(robot,id));
  }
  @Transactional public DeploymentResponse cancel(UUID owner, UUID robot, UUID id) {
    ownedRobot(owner,robot); Deployment d=ownedDeployment(robot,id);
    try { d.cancel(); } catch(IllegalStateException e) { throw new BusinessConflictException("STATE_CONFLICT",e.getMessage()); }
    return map(d);
  }
  private Robot ownedRobot(UUID owner,UUID id){return robots.findByIdAndOwnerId(id,owner).orElseThrow(()->new ResourceNotFoundException("Robot"));}
  private Deployment ownedDeployment(UUID robot,UUID id){return deployments.findByIdAndRobotId(id,robot).orElseThrow(()->new ResourceNotFoundException("Deployment"));}
  private DeploymentResponse map(Deployment d){return new DeploymentResponse(d.getId(),d.getRobotId(),d.getTaskVersionId(),d.getStatus(),d.getProgressPercent(),
      items.findAllByDeploymentId(d.getId()).stream().map(i->new ItemResponse(i.getId(),i.getSkillVersionId(),i.isGatePassed(),i.getStatus(),i.getDownloadedAt(),i.getVerifiedAt(),
          i.getErrorCode()==null?null:new ErrorInfo(i.getErrorCode(),i.getErrorMessage()))).toList(),
      d.getErrorCode()==null?null:new ErrorInfo(d.getErrorCode(),d.getErrorMessage()),d.getCreatedAt(),d.getUpdatedAt(),d.getAppliedAt());}
}
