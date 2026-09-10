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
import com.anssa8bu.ssafy.task.entity.TaskStepType;
import com.anssa8bu.ssafy.task.entity.TaskVersion;
import com.anssa8bu.ssafy.task.repository.TaskRepository;
import com.anssa8bu.ssafy.task.repository.TaskStepRepository;
import com.anssa8bu.ssafy.task.repository.TaskVersionRepository;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
  private static final int MAX_NESTING_DEPTH = 3;
  private static final int MAX_REPEAT_COUNT = 100;
  private static final int MAX_EXPANDED_STEPS = 1_000;
  private static final Map<String, double[]> MOTOR_RANGES = Map.of(
      "J1", new double[] {-180, 180}, "J2", new double[] {-90, 90},
      "J3", new double[] {-135, 135}, "J4", new double[] {-180, 180},
      "J5", new double[] {-120, 120}, "J6", new double[] {-360, 360});
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
    Task task = tasks.save(new Task(UUID.randomUUID(), ownerId, key,
        request.displayName().trim(), request.description()));
    if (request.steps() != null) publishVersion(task, request.steps());
    return taskResponse(task, request.steps() != null);
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
    if (request.steps() != null) publishVersion(task, request.steps());
    return taskResponse(task, request.steps() != null);
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
    VersionResponse result = publishVersion(task, request.steps());
    idempotency.complete(ownerId, "POST", path, claim, 201, "TASK_VERSION", result.id());
    return result;
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
    Set<UUID> referencedSkills = versionSteps.stream()
        .filter(step -> step.getStepType() == TaskStepType.SKILL)
        .map(TaskStep::getSkillVersionId).collect(Collectors.toSet());
    Map<UUID, SkillVersion> skillMap = skillVersions.findAllByIdIn(referencedSkills).stream()
        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));
    var issues = new java.util.ArrayList<ValidationIssue>();
    if (versionSteps.isEmpty()) issues.add(issue("EMPTY_TASK", "실행할 동작이 없습니다.", "NONE"));
    if (robot.getConnectionStatus() != RobotConnectionStatus.ONLINE)
      issues.add(issue("ROBOT_OFFLINE", "로봇이 오프라인입니다.", "WAIT"));
    if (skillMap.size() != referencedSkills.size()
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
  private VersionResponse publishVersion(Task task, List<StepRequest> requestedSteps) {
    Set<UUID> requestedSkillIds = new HashSet<>();
    int expandedSteps = validateSteps(requestedSteps, 1, requestedSkillIds);
    if (expandedSteps > MAX_EXPANDED_STEPS)
      throw new BusinessConflictException("TOO_MANY_EXPANDED_STEPS", "반복 전개 후 실행 단계는 1,000개 이하여야 합니다.");
    Map<UUID, SkillVersion> skillMap = skillVersions.findAllByIdIn(requestedSkillIds)
        .stream().collect(Collectors.toMap(SkillVersion::getId, Function.identity()));
    if (skillMap.size() != requestedSkillIds.size()
        || skillMap.values().stream().anyMatch(v -> v.getStatus() != SkillVersionStatus.READY))
      throw new BusinessConflictException("SKILL_VERSION_NOT_READY", "READY 상태가 아닌 스킬 버전이 포함되어 있습니다.");
    UUID versionId = UUID.randomUUID();
    TaskVersion version = versions.save(new TaskVersion(versionId, task.getId(), task.publishNextVersion()));
    List<TaskStep> saved = new ArrayList<>();
    saveSteps(versionId, null, requestedSteps, saved);
    return versionResponse(version, saved);
  }

  private int validateSteps(List<StepRequest> requested, int depth, Set<UUID> skillIds) {
    if (requested == null || requested.isEmpty())
      throw new BusinessConflictException("EMPTY_TASK", "실행할 단계가 필요합니다.");
    if (depth > MAX_NESTING_DEPTH)
      throw new BusinessConflictException("REPEAT_NESTING_TOO_DEEP", "반복 블록은 3단계까지만 중첩할 수 있습니다.");
    validateOrders(requested);
    int expanded = 0;
    for (StepRequest step : requested) {
      TaskStepType type;
      try { type = TaskStepType.valueOf(step.type().toUpperCase(Locale.ROOT)); }
      catch (IllegalArgumentException exception) {
        throw new BusinessConflictException("INVALID_STEP_TYPE", "지원하지 않는 작업 단계입니다.");
      }
      switch (type) {
        case SKILL -> {
          if (step.skillVersionId() == null)
            throw new BusinessConflictException("SKILL_VERSION_REQUIRED", "스킬 단계에는 스킬 버전이 필요합니다.");
          skillIds.add(step.skillVersionId());
          expanded++;
        }
        case MOTOR -> {
          validateMotor(step.parameters());
          expanded++;
        }
        case REPEAT -> {
          if (step.repeatCount() == null || step.repeatCount() > MAX_REPEAT_COUNT)
            throw new BusinessConflictException("INVALID_REPEAT_COUNT", "반복 횟수는 1~100이어야 합니다.");
          expanded += step.repeatCount() * validateSteps(step.children(), depth + 1, skillIds);
        }
      }
    }
    return expanded;
  }

  private void validateMotor(Map<String, Object> parameters) {
    Object motorValue = parameters == null ? null : parameters.get("motor");
    Object angleValue = parameters == null ? null : parameters.get("angle");
    double[] range = motorValue instanceof String motor ? MOTOR_RANGES.get(motor) : null;
    if (range == null || !(angleValue instanceof Number angle)
        || angle.doubleValue() < range[0] || angle.doubleValue() > range[1])
      throw new BusinessConflictException("INVALID_MOTOR_COMMAND", "모터 이름과 허용 각도를 확인해 주세요.");
  }

  private void saveSteps(UUID versionId, UUID parentId, List<StepRequest> requested,
      List<TaskStep> saved) {
    for (StepRequest request : requested) {
      TaskStepType type = TaskStepType.valueOf(request.type().toUpperCase(Locale.ROOT));
      UUID id = UUID.randomUUID();
      TaskStep entity = steps.save(new TaskStep(id, versionId, parentId, type,
          request.skillVersionId(), request.order(), request.parameters(),
          request.timeoutSeconds(), request.repeatCount()));
      saved.add(entity);
      if (type == TaskStepType.REPEAT) saveSteps(versionId, id, request.children(), saved);
    }
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
    return new VersionResponse(version.getId(), version.getTaskId(), version.getVersion(),
        stepResponses(items, null), version.getCreatedAt());
  }
  private List<StepResponse> stepResponses(List<TaskStep> items, UUID parentId) {
    return items.stream().filter(step -> Objects.equals(step.getParentStepId(), parentId))
        .sorted(Comparator.comparingInt(TaskStep::getStepOrder))
        .map(step -> new StepResponse(step.getStepOrder(), step.getStepType().name(),
            step.getSkillVersionId(), step.getParameters(), step.getTimeoutSeconds(),
            step.getRepeatCount(), stepResponses(items, step.getId()))).toList();
  }
  private ValidationIssue issue(String c, String m, String a) { return new ValidationIssue(c, m, a); }
}
