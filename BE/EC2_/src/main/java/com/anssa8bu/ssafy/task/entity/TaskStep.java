package com.anssa8bu.ssafy.task.entity;

import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_steps")
public class TaskStep {
  @Id private UUID id;

  @Column(name = "task_version_id", nullable = false)
  private UUID taskVersionId;

  @Column(name = "skill_version_id")
  private UUID skillVersionId;

  @Column(name = "parent_step_id")
  private UUID parentStepId;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_type", nullable = false, length = 20)
  private TaskStepType stepType;

  @Column(name = "step_order", nullable = false)
  private int stepOrder;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> parameters;

  @Column(name = "timeout_seconds")
  private Integer timeoutSeconds;

  @Column(name = "repeat_count")
  private Integer repeatCount;

  protected TaskStep() {}

  public TaskStep(
      UUID id,
      UUID taskVersionId,
      UUID parentStepId,
      TaskStepType stepType,
      UUID skillVersionId,
      int stepOrder,
      Map<String, Object> parameters,
      Integer timeoutSeconds,
      Integer repeatCount) {
    this.id = Objects.requireNonNull(id);
    this.taskVersionId = Objects.requireNonNull(taskVersionId);
    this.parentStepId = parentStepId;
    this.stepType = Objects.requireNonNull(stepType);
    this.skillVersionId = skillVersionId;
    if (stepOrder < 1) throw new IllegalArgumentException("stepOrder must be positive");
    if (timeoutSeconds != null && timeoutSeconds < 1)
      throw new IllegalArgumentException("timeoutSeconds must be positive");
    this.stepOrder = stepOrder;
    this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    this.timeoutSeconds = timeoutSeconds;
    this.repeatCount = repeatCount;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTaskVersionId() {
    return taskVersionId;
  }

  public UUID getSkillVersionId() {
    return skillVersionId;
  }

  public UUID getParentStepId() {
    return parentStepId;
  }

  public TaskStepType getStepType() {
    return stepType;
  }

  public int getStepOrder() {
    return stepOrder;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public Integer getRepeatCount() {
    return repeatCount;
  }
}
