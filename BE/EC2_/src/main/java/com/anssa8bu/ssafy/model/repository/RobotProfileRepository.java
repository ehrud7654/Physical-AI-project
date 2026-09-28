package com.anssa8bu.ssafy.model.repository;

import com.anssa8bu.ssafy.model.entity.RobotProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotProfileRepository extends JpaRepository<RobotProfile, UUID> {
  Optional<RobotProfile> findFirstByOrderByCreatedAtAsc();
}
