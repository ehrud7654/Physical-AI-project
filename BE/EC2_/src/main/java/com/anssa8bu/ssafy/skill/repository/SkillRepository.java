package com.anssa8bu.ssafy.skill.repository;

import com.anssa8bu.ssafy.skill.entity.Skill;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
  List<Skill> findAllByOrderByDisplayNameAsc();

  Optional<Skill> findBySkillKey(String skillKey);

  Optional<Skill> findByIdAndOwnerId(UUID id, UUID ownerId);

  boolean existsByOwnerIdAndDisplayNameIgnoreCase(UUID ownerId, String displayName);

  @Query(
      "select s from Skill s where s.ownerId is null or s.ownerId = :ownerId order by s.displayName")
  List<Skill> findVisibleByOwnerId(@Param("ownerId") UUID ownerId);

  @Query(
      "select s from Skill s where s.skillKey = :skillKey and (s.ownerId is null or s.ownerId = :ownerId)")
  Optional<Skill> findBySkillKeyAndVisibleToOwner(
      @Param("skillKey") String skillKey, @Param("ownerId") UUID ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Skill s where s.id=:id")
  Optional<Skill> findByIdForUpdate(@Param("id") UUID id);
}
