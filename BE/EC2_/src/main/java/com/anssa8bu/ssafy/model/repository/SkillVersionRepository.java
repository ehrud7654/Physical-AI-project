package com.anssa8bu.ssafy.model.repository;

import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.entity.SkillVersionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillVersionRepository extends JpaRepository<SkillVersion, UUID> {
  List<SkillVersion> findAllByIdIn(Collection<UUID> ids);
  Optional<SkillVersion> findFirstBySkillIdAndStatusAndGatePassedTrueOrderByVersionDesc(
      UUID skillId, SkillVersionStatus status);
}
