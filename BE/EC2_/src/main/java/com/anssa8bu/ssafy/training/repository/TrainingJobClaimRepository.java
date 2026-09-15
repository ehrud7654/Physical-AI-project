package com.anssa8bu.ssafy.training.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingJobClaimRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public TrainingJobClaimRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

  public Optional<UUID> lockNextCandidate(Set<String> algorithms) {
    String sql="""
        SELECT j.id
          FROM training_jobs j
          JOIN training_profiles p ON p.id=j.training_profile_id
         WHERE j.deleted_at IS NULL
           AND j.status IN ('CREATED','QUEUED')
           AND p.algorithm IN (:algorithms)
         ORDER BY j.created_at
         FOR UPDATE OF j SKIP LOCKED
         LIMIT 1
        """;
    List<UUID> result=jdbc.query(sql,new MapSqlParameterSource("algorithms",algorithms),
        (rs,row)->rs.getObject("id",UUID.class));
    return result.stream().findFirst();
  }
}
