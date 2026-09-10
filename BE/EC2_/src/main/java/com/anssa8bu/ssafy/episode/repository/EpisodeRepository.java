package com.anssa8bu.ssafy.episode.repository;

import com.anssa8bu.ssafy.episode.entity.Episode;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {
  List<Episode> findAllByIdIn(Collection<UUID> ids);
  List<Episode> findAllByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId);
  Optional<Episode> findByIdAndOwnerId(UUID id, UUID ownerId);
}
