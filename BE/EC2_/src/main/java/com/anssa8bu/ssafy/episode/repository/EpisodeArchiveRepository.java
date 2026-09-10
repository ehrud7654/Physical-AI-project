package com.anssa8bu.ssafy.episode.repository;

import com.anssa8bu.ssafy.episode.entity.EpisodeArchive;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeArchiveRepository extends JpaRepository<EpisodeArchive, UUID> {
  Optional<EpisodeArchive> findByEpisodeId(UUID episodeId);
  List<EpisodeArchive> findAllByEpisodeIdIn(Collection<UUID> episodeIds);
}
