package com.anssa8bu.ssafy.event;

import static org.assertj.core.api.Assertions.*;

import com.anssa8bu.ssafy.event.application.*;
import com.anssa8bu.ssafy.event.entity.*;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import com.anssa8bu.ssafy.user.entity.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.outbox.enabled=false")
class OutboxTransactionIntegrationTest {
  @Autowired TransactionalOutboxService service;
  @Autowired OutboxEventRepository outbox;
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  UUID committedUser;
  String duplicateEvent;

  @BeforeEach
  void seed() {
    committedUser = UUID.randomUUID();
    duplicateEvent = "duplicate-" + UUID.randomUUID();
    service.execute(
        () -> {
          em.persist(user(committedUser));
          return null;
        },
        event(duplicateEvent, committedUser),
        1L);
  }

  @AfterEach
  void clean() {
    jdbc.update(
        "delete from outbox_events where aggregate_id in (?,?)",
        committedUser.toString(),
        "rollback");
    jdbc.update("delete from users where email like 'outbox-%@test.local'");
  }

  @Test
  void commitsBusinessChangeAndOutboxTogether() {
    assertThat(count("users", committedUser)).isOne();
    assertThat(outbox.findByEventId(duplicateEvent)).isPresent();
  }

  @Test
  void rollsBackBusinessChangeWhenOutboxInsertFails() {
    UUID rollbackUser = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                service.execute(
                    () -> {
                      em.persist(user(rollbackUser));
                      return null;
                    },
                    event(duplicateEvent, rollbackUser),
                    1L))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("users", rollbackUser)).isZero();
  }

  private User user(UUID id) {
    return new User(id, "outbox-" + id + "@test.local", "hash", "name", UserStatus.ACTIVE);
  }

  private DomainEventPayload event(String eventId, UUID owner) {
    return new DomainEventPayload(
        1,
        eventId,
        "USER_CREATED",
        "USER",
        owner.toString(),
        owner,
        Instant.now(),
        Map.of("ownerId", owner.toString()));
  }

  private int count(String table, UUID id) {
    return jdbc.queryForObject("select count(*) from " + table + " where id=?", Integer.class, id);
  }
}
