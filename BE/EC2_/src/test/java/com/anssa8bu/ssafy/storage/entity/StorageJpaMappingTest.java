package com.anssa8bu.ssafy.storage.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StorageJpaMappingTest {

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID storageNodeId;
  private UUID retentionPolicyId;

  @Test
  void persistsFlushesClearsAndReadsStorageDomain() {
    storageNodeId = UUID.randomUUID();
    retentionPolicyId = UUID.randomUUID();
    String uniqueSuffix = storageNodeId.toString();

    StorageNode storageNode =
        new StorageNode(
            storageNodeId,
            "test-worker-" + uniqueSuffix,
            "test-node-" + uniqueSuffix,
            "테스트 저장소",
            StorageType.LOCAL,
            StorageNodeStatus.OFFLINE,
            2_199_023_255_552L,
            1_099_511_627_776L);
    RetentionPolicy retentionPolicy =
        new RetentionPolicy(
            retentionPolicyId,
            "test-retention-" + uniqueSuffix,
            7,
            3,
            30,
            3,
            true,
            true,
            false,
            true);

    entityManager.persist(storageNode);
    entityManager.persist(retentionPolicy);
    entityManager.flush();
    entityManager.clear();

    StorageNode foundNode = entityManager.find(StorageNode.class, storageNodeId);
    RetentionPolicy foundPolicy = entityManager.find(RetentionPolicy.class, retentionPolicyId);

    assertThat(foundNode.getId()).isEqualTo(storageNodeId);
    assertThat(foundNode.getStorageType()).isEqualTo(StorageType.LOCAL);
    assertThat(foundNode.getStatus()).isEqualTo(StorageNodeStatus.OFFLINE);
    assertThat(foundNode.getCapacityBytes()).isEqualTo(2_199_023_255_552L);
    assertThat(foundNode.getAvailableBytes()).isEqualTo(1_099_511_627_776L);
    assertThat(foundNode.getLastSeenAt()).isNull();
    assertThat(foundNode.getRowVersion()).isZero();
    assertThat(foundNode.getCreatedAt()).isNotNull();
    assertThat(foundNode.getUpdatedAt()).isNotNull();

    assertThat(foundPolicy.getId()).isEqualTo(retentionPolicyId);
    assertThat(foundPolicy.getDefaultRetentionDays()).isEqualTo(7);
    assertThat(foundPolicy.getPostTrainingGraceDays()).isEqualTo(3);
    assertThat(foundPolicy.getMaxRetentionDays()).isEqualTo(30);
    assertThat(foundPolicy.getMaxDeleteAttempts()).isEqualTo(3);
    assertThat(foundPolicy.isAllowManualExtension()).isTrue();
    assertThat(foundPolicy.isAllowManualPurge()).isTrue();
    assertThat(foundPolicy.isDefaultPolicy()).isFalse();
    assertThat(foundPolicy.isActive()).isTrue();
    assertThat(foundPolicy.getCreatedAt()).isNotNull();
    assertThat(foundPolicy.getUpdatedAt()).isNotNull();

    Instant heartbeatAt = Instant.parse("2026-09-08T03:21:45.123456Z");
    foundNode.reportHeartbeat(
        StorageNodeStatus.ONLINE, 2_199_023_255_552L, 900_000_000_000L, heartbeatAt);
    entityManager.flush();
    entityManager.clear();

    StorageNode updatedNode = entityManager.find(StorageNode.class, storageNodeId);
    assertThat(updatedNode.getStatus()).isEqualTo(StorageNodeStatus.ONLINE);
    assertThat(updatedNode.getAvailableBytes()).isEqualTo(900_000_000_000L);
    assertThat(updatedNode.getLastSeenAt()).isEqualTo(heartbeatAt);
    assertThat(updatedNode.getRowVersion()).isEqualTo(1L);
  }

  @AfterTransaction
  void insertedRowsAreRolledBack() {
    if (storageNodeId == null) {
      return;
    }

    assertThat(count("SELECT count(*) FROM storage_nodes WHERE id = ?", storageNodeId)).isZero();
    assertThat(count("SELECT count(*) FROM retention_policies WHERE id = ?", retentionPolicyId))
        .isZero();
  }

  private long count(String sql, UUID id) {
    Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
    return count == null ? 0L : count;
  }
}
