package com.anssa8bu.ssafy.skill.application;

import static com.anssa8bu.ssafy.skill.dto.SkillApiModels.CreateCustomSkillRequest;
import static com.anssa8bu.ssafy.skill.dto.SkillResponses.SkillResponse;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.entity.SkillStatus;
import com.anssa8bu.ssafy.skill.entity.SkillTier;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {
  private static final String CREATE_PATH = "/api/v1/skills";
  private final SkillRepository skills;
  private final IdempotencyGuard idempotency;

  public SkillService(SkillRepository skills, IdempotencyGuard idempotency) {
    this.skills = skills;
    this.idempotency = idempotency;
  }

  @Transactional(readOnly = true)
  public List<SkillResponse> listVisible(UUID ownerId) {
    return skills.findVisibleByOwnerId(ownerId).stream().map(this::toResponse).toList();
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
