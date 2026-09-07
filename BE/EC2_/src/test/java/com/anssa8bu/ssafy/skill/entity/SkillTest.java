package com.anssa8bu.ssafy.skill.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillTest {

    @Test
    void tutorialSkillAllowsNullPersona() {
        Skill skill = new Skill(
                UUID.randomUUID(),
                "pick_place",
                SkillTier.TUTORIAL,
                SkillStatus.PLANNED,
                "집어 옮기기",
                null,
                false,
                null
        );

        assertThat(skill.getTier()).isEqualTo(SkillTier.TUTORIAL);
        assertThat(skill.getStatus()).isEqualTo(SkillStatus.PLANNED);
        assertThat(skill.getPersona()).isNull();
    }

    @Test
    void demoSkillRequiresPersona() {
        assertThatThrownBy(() -> new Skill(
                UUID.randomUUID(),
                "demo_skill",
                SkillTier.DEMO,
                SkillStatus.PLANNED,
                "데모 스킬",
                null,
                false,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DEMO skill requires persona");
    }
}
