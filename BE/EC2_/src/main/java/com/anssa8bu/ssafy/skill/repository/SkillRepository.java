package com.anssa8bu.ssafy.skill.repository;

import com.anssa8bu.ssafy.skill.entity.Skill;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
  List<Skill> findAllByOrderByDisplayNameAsc();
  Optional<Skill> findBySkillKey(String skillKey);
}
