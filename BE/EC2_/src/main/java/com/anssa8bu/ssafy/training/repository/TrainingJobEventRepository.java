package com.anssa8bu.ssafy.training.repository;

import com.anssa8bu.ssafy.training.entity.TrainingJobEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingJobEventRepository extends JpaRepository<TrainingJobEvent, UUID> {
  Optional<TrainingJobEvent> findByTrainingJobIdAndAttemptAndEventId(UUID jobId, int attempt, String eventId);
  Optional<TrainingJobEvent> findFirstByTrainingJobIdAndAttemptOrderBySequenceDesc(UUID jobId, int attempt);
}
