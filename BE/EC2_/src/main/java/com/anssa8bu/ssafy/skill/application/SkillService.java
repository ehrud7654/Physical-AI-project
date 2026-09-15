package com.anssa8bu.ssafy.skill.application;

import static com.anssa8bu.ssafy.skill.dto.SkillApiModels.CreateCustomSkillRequest;
import static com.anssa8bu.ssafy.skill.dto.SkillApiModels.UpdateSkillDescriptionRequest;
import static com.anssa8bu.ssafy.skill.dto.SkillResponses.SkillResponse;
import static com.anssa8bu.ssafy.skill.dto.SkillResponses.SkillOverviewResponse;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.entity.SkillStatus;
import com.anssa8bu.ssafy.skill.entity.SkillTier;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.training.entity.TrainingJob;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {
  private static final String CREATE_PATH = "/api/v1/skills";
  private final SkillRepository skills;
  private final SkillVersionRepository versions;
  private final TrainingJobRepository trainingJobs;
  private final IdempotencyGuard idempotency;

  public SkillService(
      SkillRepository skills,
      SkillVersionRepository versions,
      TrainingJobRepository trainingJobs,
      IdempotencyGuard idempotency) {
    this.skills = skills;
    this.versions = versions;
    this.trainingJobs = trainingJobs;
    this.idempotency = idempotency;
  }

  @Transactional(readOnly = true)
  public List<SkillResponse> listVisible(UUID ownerId) {
    return skills.findVisibleByOwnerId(ownerId).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<SkillOverviewResponse> listOverview(UUID ownerId) {
    List<Skill> visibleSkills = skills.findVisibleByOwnerId(ownerId);
    Set<UUID> skillIds = visibleSkills.stream().map(Skill::getId).collect(Collectors.toSet());

    Map<UUID, SkillVersion> latestVersions = versions.findAllBySkillIdIn(skillIds).stream()
        .collect(Collectors.toMap(
            SkillVersion::getSkillId,
            Function.identity(),
            (left, right) -> left.getVersion() >= right.getVersion() ? left : right));
    Map<UUID, Instant> latestRequests = trainingJobs
        .findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId).stream()
        .filter(job -> skillIds.contains(job.getSkillId()))
        .collect(Collectors.toMap(
            TrainingJob::getSkillId,
            TrainingJob::getCreatedAt,
            (left, right) -> left.isAfter(right) ? left : right));

    return visibleSkills.stream().map(skill -> {
      SkillVersion latest = latestVersions.get(skill.getId());
      return new SkillOverviewResponse(
          skill.getId(),
          skill.getDisplayName(),
          skill.getDescription(),
          latest == null ? null : latest.getVersion(),
          latest == null ? null : latest.getCreatedAt(),
          skill.getStatus(),
          latestRequests.get(skill.getId()));
    }).toList();
  }

  @Transactional
  public SkillResponse create(
      UUID ownerId, String idempotencyKey, CreateCustomSkillRequest request) {
    var claim = idempotency.claim(ownerId, "POST", CREATE_PATH, idempotencyKey, request);
    if (claim.existingResourceId() != null) {
      Skill existing =
          skills
              .findByIdAndOwnerId(claim.existingResourceId(), ownerId)
              .orElseThrow(() -> new ResourceNotFoundException("Skill"));
      return toResponse(existing);
    }
    Skill skill = createCustom(ownerId, request);
    idempotency.complete(ownerId, "POST", CREATE_PATH, claim, 201, "SKILL", skill.getId());
    return toResponse(skill);
  }

  public Skill createCustom(UUID ownerId, CreateCustomSkillRequest request) {
    String displayName = request.displayName().trim();
    if (skills.existsByOwnerIdAndDisplayNameIgnoreCase(ownerId, displayName)) {
      throw new BusinessConflictException("SKILL_NAME_DUPLICATED", "같은 이름의 동작이 이미 존재합니다.");
    }
    String skillKey =
        "custom_" + UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    return skills.save(
        new Skill(
            UUID.randomUUID(),
            ownerId,
            skillKey,
            SkillTier.CUSTOM,
            SkillStatus.PLANNED,
            displayName,
            request.description(),
            null));
  }

  @Transactional
  public SkillResponse updateDescription(
      UUID ownerId, UUID skillId, UpdateSkillDescriptionRequest request) {
    Skill skill = skills.findById(skillId)
        .filter(candidate -> candidate.getOwnerId() == null || ownerId.equals(candidate.getOwnerId()))
        .orElseThrow(() -> new ResourceNotFoundException("Skill"));
    skill.updateDescription(request.description());
    return toResponse(skill);
  }

  public Skill requireVisible(UUID ownerId, String skillKey) {
    return skills
        .findBySkillKeyAndVisibleToOwner(skillKey, ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Skill"));
  }

  private SkillResponse toResponse(Skill skill) {
    return new SkillResponse(
        skill.getId(),
        skill.getSkillKey(),
        skill.getTier(),
        skill.getStatus(),
        skill.getDisplayName(),
        skill.getDescription(),
        skill.getPersona(),
        skill.getCreatedAt(),
        skill.getUpdatedAt());
  }
}
