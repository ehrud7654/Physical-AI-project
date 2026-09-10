package com.anssa8bu.ssafy.training.repository;

import com.anssa8bu.ssafy.training.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingProfileRepository extends JpaRepository<TrainingProfile, UUID> {
  Optional<TrainingProfile> findFirstByStatusOrderByVersionDesc(TrainingProfileStatus status);
}
