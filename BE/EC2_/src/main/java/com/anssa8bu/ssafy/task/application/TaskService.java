package com.anssa8bu.ssafy.task.application;

import static com.anssa8bu.ssafy.task.dto.TaskApiModels.*;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.entity.SkillVersionStatus;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.robot.entity.RobotConnectionStatus;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import com.anssa8bu.ssafy.task.entity.Task;
import com.anssa8bu.ssafy.task.entity.TaskStep;
import com.anssa8bu.ssafy.task.entity.TaskVersion;
import com.anssa8bu.ssafy.task.repository.TaskRepository;
import com.anssa8bu.ssafy.task.repository.TaskStepRepository;
import com.anssa8bu.ssafy.task.repository.TaskVersionRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
  private final TaskRepository tasks;
  private final TaskVersionRepository versions;
  private final TaskStepRepository steps;
  private final SkillVersionRepository skillVersions;
  private final RobotRepository robots;
  private final Clock clock;
  private final IdempotencyGuard idempotency;

  public TaskService(TaskRepository tasks, TaskVersionRepository versions, TaskStepRepository steps,
      SkillVersionRepository skillVersions, RobotRepository robots, Clock clock,
      IdempotencyGuard idempotency) {
    this.tasks = tasks; this.versions = versions; this.steps = steps;
    this.skillVersions = skillVersions; this.robots = robots; this.clock = clock;
    this.idempotency = idempotency;
  }

  @Transactional
  public TaskResponse create(UUID ownerId, CreateRequest request) {
    String key = request.taskKey().trim();
    if (tasks.existsByOwnerIdAndTaskKey(ownerId, key))
      throw new BusinessConflictException("TASK_KEY_ALREADY_EXISTS", "이미 사용 중인 taskKey입니다.");
    return taskResponse(tasks.save(new Task(UUID.randomUUID(), ownerId, key,
        request.displayName().trim(), request.description())), false);
  }

  @Transactional(readOnly = true)
  public List<TaskResponse> list(UUID ownerId, String q, boolean includeLatestVersion) {
    List<Task> found = q == null || q.isBlank()
        ? tasks.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)
        : tasks.findAllByOwnerIdAndDisplayNameContainingIgnoreCaseOrderByUpdatedAtDesc(ownerId, q.trim());
    return found.stream().map(task -> taskResponse(task, includeLatestVersion)).toList();
  }

  @Transactional(readOnly = true)
  public TaskResponse get(UUID ownerId, UUID taskId, boolean includeLatestVersion) {
    return taskResponse(ownedTask(ownerId, taskId), includeLatestVersion);
  }

  @Transactional
  public TaskResponse update(UUID ownerId, UUID taskId, UpdateRequest request) {
    Task task = tasks.findOwnedForUpdate(taskId, ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Task"));
    task.update(request.displayName(), request.description());
    return taskResponse(task, false);
  }

  @Transactional
  public VersionResponse createVersion(UUID ownerId, UUID taskId, String idempotencyKey,
      VersionCreateRequest request) {
    String path = "/api/v1/tasks/" + taskId + "/versions";
    var claim = idempotency.claim(ownerId, "POST", path, idempotencyKey, request);
    if (claim.existingResourceId() != null) {
      return getVersion(ownerId, taskId, claim.existingResourceId());
    }
    Task task = tasks.findOwnedForUpdate(taskId, ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Task"));
    validateOrders(request.steps());
    Map<UUID, SkillVersion> skillMap = skillVersions.findAllByIdIn(
        request.steps().stream().map(StepRequest::skillVersionId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(SkillVersion::getId, Function.identity()));
    if (skillMap.size() != new HashSet<>(request.steps().stream().map(StepRequest::skillVersionId).toList()).size()
        || skillMap.values().stream().anyMatch(v -> v.getStatus() != SkillVersionStatus.READY))
      throw new BusinessConflictException("SKILL_VERSION_NOT_READY", "READY 상태가 아닌 스킬 버전이 포함되어 있습니다.");
    UUID versionId = UUID.randomUUID();
    TaskVersion version = versions.save(new TaskVersion(versionId, taskId, task.publishNextVersion()));
    List<TaskStep> saved = steps.saveAll(request.steps().stream().map(s -> new TaskStep(
        UUID.randomUUID(), versionId, s.skillVersionId(), s.order(), s.parameters(), s.timeoutSeconds())).toList());
    idempotency.complete(ownerId, "POST", path, claim, 201, "TASK_VERSION", versionId);
    return versionResponse(version, saved);
  }

  @Transactional(readOnly = true)
  public List<VersionResponse> listVersions(UUID ownerId, UUID taskId) {
    ownedTask(ownerId, taskId);
    return versions.findAllByTaskIdOrderByVersionDesc(taskId).stream().map(this::versionResponse).toList();
  }

  @Transactional(readOnly = true)
  public VersionResponse getVersion(UUID ownerId, UUID taskId, UUID versionId) {
    ownedTask(ownerId, taskId);
    return versionResponse(versions.findByIdAndTaskId(versionId, taskId)
        .orElseThrow(() -> new ResourceNotFoundException("TaskVersion")));
  }

  @Transactional(readOnly = true)
  public ValidationResponse validate(UUID ownerId, UUID taskId, UUID versionId, UUID robotId) {
    TaskVersion version = getOwnedVersion(ownerId, taskId, versionId);
    Robot robot = robots.findByIdAndOwnerId(robotId, ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Robot"));
    List<TaskStep> versionSteps = steps.findAllByTaskVersionIdOrderByStepOrder(versionId);
    Map<UUID, SkillVersion> skillMap = skillVersions.findAllByIdIn(
        versionSteps.stream().map(TaskStep::getSkillVersionId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));
    var issues = new java.util.ArrayList<ValidationIssue>();
    if (versionSteps.isEmpty()) issues.add(issue("EMPTY_TASK", "실행할 동작이 없습니다.", "NONE"));
    if (robot.getConnectionStatus() != RobotConnectionStatus.ONLINE)
      issues.add(issue("ROBOT_OFFLINE", "로봇이 오프라인입니다.", "WAIT"));
    if (skillMap.size() != versionSteps.stream().map(TaskStep::getSkillVersionId).distinct().count()
        || skillMap.values().stream().anyMatch(v -> v.getStatus() != SkillVersionStatus.READY || !v.isGatePassed()))
      issues.add(issue("SKILL_VERSION_NOT_READY", "배포할 수 없는 스킬 버전이 포함되어 있습니다.", "RETRY"));
    if (skillMap.values().stream().anyMatch(v -> !v.getRobotProfileId().equals(robot.getRobotProfileId())))
      issues.add(issue("MODEL_INCOMPATIBLE", "로봇 프로파일과 호환되지 않습니다.", "CONTACT_SUPPORT"));
    String readiness = issues.isEmpty() ? "READY" : "BLOCKED";
    return new ValidationResponse(issues.isEmpty(), readiness, robotId, version.getId(),
        robot.getCurrentDeploymentId(), clock.instant(), List.copyOf(issues), List.of());
  }

  private Task ownedTask(UUID ownerId, UUID taskId) { return tasks.findByIdAndOwnerId(taskId, ownerId)
      .orElseThrow(() -> new ResourceNotFoundException("Task")); }
  private TaskVersion getOwnedVersion(UUID ownerId, UUID taskId, UUID versionId) {
    ownedTask(ownerId, taskId); return versions.findByIdAndTaskId(versionId, taskId)
        .orElseThrow(() -> new ResourceNotFoundException("TaskVersion")); }
  private void validateOrders(List<StepRequest> items) {
    for (int i = 0; i < items.size(); i++) if (items.get(i).order() != i + 1)
      throw new BusinessConflictException("INVALID_STEP_ORDER", "step order는 1부터 연속이어야 합니다.");
  }
  private TaskResponse taskResponse(Task task, boolean include) {
    VersionResponse latest = include ? versions.findFirstByTaskIdOrderByVersionDesc(task.getId())
        .map(this::versionResponse).orElse(null) : null;
    return new TaskResponse(task.getId(), task.getOwnerId(), task.getTaskKey(), task.getDisplayName(),
        task.getDescription(), task.getLatestVersion(), latest, task.getCreatedAt(), task.getUpdatedAt());
  }
  private VersionResponse versionResponse(TaskVersion version) {
    return versionResponse(version, steps.findAllByTaskVersionIdOrderByStepOrder(version.getId()));
  }
  private VersionResponse versionResponse(TaskVersion version, List<TaskStep> items) {
    return new VersionResponse(version.getId(), version.getTaskId(), version.getVersion(), items.stream()
        .map(s -> new StepResponse(s.getStepOrder(), s.getSkillVersionId(), s.getParameters(), s.getTimeoutSeconds())).toList(), version.getCreatedAt());
  }
  private ValidationIssue issue(String c, String m, String a) { return new ValidationIssue(c, m, a); }
}
