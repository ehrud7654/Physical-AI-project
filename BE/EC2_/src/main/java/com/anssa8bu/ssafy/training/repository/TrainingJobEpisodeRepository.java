package com.anssa8bu.ssafy.training.repository;

import com.anssa8bu.ssafy.training.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingJobEpisodeRepository
    extends JpaRepository<TrainingJobEpisode, TrainingJobEpisodeId> {
  List<TrainingJobEpisode> findAllByIdTrainingJobIdOrderByInputOrder(UUID trainingJobId);

  long countByIdTrainingJobId(UUID trainingJobId);
}
