package com.anssa8bu.ssafy.training.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingJobClaimRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public TrainingJobClaimRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

  public Optional<UUID> lockNextCandidate() {
    String sql="""
        SELECT j.id
          FROM training_jobs j
         WHERE j.deleted_at IS NULL
           AND j.status IN ('CREATED','QUEUED')
         ORDER BY j.created_at
         FOR UPDATE OF j SKIP LOCKED
         LIMIT 1
        """;
    List<UUID> result=jdbc.query(sql,new MapSqlParameterSource(),
        (rs,row)->rs.getObject("id",UUID.class));
    return result.stream().findFirst();
  }
}
