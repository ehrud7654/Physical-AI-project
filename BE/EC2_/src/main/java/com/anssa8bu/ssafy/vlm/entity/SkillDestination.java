package com.anssa8bu.ssafy.vlm.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "skill_destinations")
public class SkillDestination extends AuditableEntity {
  @EmbeddedId private SkillDestinationId id;

  @Column(name = "display_name", nullable = false, length = 150)
  private String displayName;

  @Column(nullable = false)
  private boolean enabled;

  protected SkillDestination() {}

  public SkillDestination(SkillDestinationId id, String name, boolean enabled) {
    this.id = id;
    this.displayName = name;
    this.enabled = enabled;
  }

  public SkillDestinationId getId() {
    return id;
  }
}
