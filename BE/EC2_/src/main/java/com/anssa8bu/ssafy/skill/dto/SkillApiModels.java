package com.anssa8bu.ssafy.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SkillApiModels {
  private SkillApiModels() {}

  public record CreateCustomSkillRequest(
      @NotBlank @Size(max = 150) String displayName, @Size(max = 2000) String description) {}

  public record UpdateSkillDescriptionRequest(@Size(max = 2000) String description) {}
}
