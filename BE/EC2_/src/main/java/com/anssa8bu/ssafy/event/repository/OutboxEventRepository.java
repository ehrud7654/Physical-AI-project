package com.anssa8bu.ssafy.event.repository;

import com.anssa8bu.ssafy.event.entity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      value =
          "SELECT id FROM outbox_events WHERE status IN ('PENDING','FAILED') AND available_at <= :now ORDER BY available_at,created_at FOR UPDATE SKIP LOCKED LIMIT :batch",
      nativeQuery = true)
  List<UUID> lockPublishableIds(@Param("now") Instant now, @Param("batch") int batch);

  @Query(
      value =
          "SELECT id FROM outbox_events WHERE status='PROCESSING' AND lease_expires_at <= :now ORDER BY lease_expires_at FOR UPDATE SKIP LOCKED LIMIT :batch",
      nativeQuery = true)
  List<UUID> lockExpiredIds(@Param("now") Instant now, @Param("batch") int batch);

  Optional<OutboxEvent> findByEventId(String eventId);

  @Modifying
  @Query(
      "delete from OutboxEvent e where e.status=com.anssa8bu.ssafy.event.entity.OutboxStatus.PUBLISHED and e.publishedAt<:cutoff")
  int deletePublishedBefore(@Param("cutoff") Instant cutoff);

  long countByStatus(OutboxStatus status);
}
