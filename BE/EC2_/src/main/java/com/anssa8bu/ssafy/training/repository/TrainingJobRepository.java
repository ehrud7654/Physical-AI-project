package com.anssa8bu.ssafy.training.repository;

import com.anssa8bu.ssafy.training.entity.TrainingJob;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingJobRepository extends JpaRepository<TrainingJob, UUID> {
  List<TrainingJob> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
  Optional<TrainingJob> findByIdAndOwnerId(UUID id, UUID ownerId);
}
