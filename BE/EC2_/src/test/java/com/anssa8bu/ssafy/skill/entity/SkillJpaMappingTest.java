package com.anssa8bu.ssafy.skill.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SkillJpaMappingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReadsUuidEnumsAndAuditingTimestamps() {
        UUID skillId = UUID.randomUUID();
        Skill skill = new Skill(
                skillId,
                "mapping_test_" + skillId.toString().replace("-", "").substring(0, 12),
                SkillTier.TUTORIAL,
                SkillStatus.PLANNED,
                "JPA 매핑 테스트",
                null,
                false,
                null
        );

        entityManager.persist(skill);
        entityManager.flush();
        entityManager.clear();

        Skill found = entityManager.find(Skill.class, skillId);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(skillId);
        assertThat(found.getTier()).isEqualTo(SkillTier.TUTORIAL);
        assertThat(found.getStatus()).isEqualTo(SkillStatus.PLANNED);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getCreatedAt()).isEqualTo(found.getUpdatedAt());
    }
}
