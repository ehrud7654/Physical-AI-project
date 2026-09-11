package com.anssa8bu.ssafy.robot.repository;

import com.anssa8bu.ssafy.robot.entity.Robot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<Robot, UUID> {
  List<Robot> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

  Optional<Robot> findByIdAndOwnerId(UUID id, UUID ownerId);
}
