package com.anssa8bu.ssafy.skill.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "skills")
public class Skill extends AuditableEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "skill_key", nullable = false, updatable = false, length = 100)
  private String skillKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "tier", nullable = false, length = 20)
  private SkillTier tier;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private SkillStatus status;

  @Column(name = "display_name", nullable = false, length = 150)
  private String displayName;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "persona", columnDefinition = "text")
  private String persona;

  protected Skill() {}

  public Skill(
      UUID id,
      String skillKey,
      SkillTier tier,
      SkillStatus status,
      String displayName,
      String description,
      String persona) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.skillKey = requireText(skillKey, "skillKey");
    this.tier = Objects.requireNonNull(tier, "tier must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.displayName = requireText(displayName, "displayName");
    this.description = description;
    this.persona = persona;

    if (tier == SkillTier.DEMO && (persona == null || persona.isBlank())) {
      throw new IllegalArgumentException("DEMO skill requires persona");
    }
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public String getSkillKey() {
    return skillKey;
  }

  public SkillTier getTier() {
    return tier;
  }

  public SkillStatus getStatus() {
    return status;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public String getPersona() {
    return persona;
  }
}
