package com.anssa8bu.ssafy.storage.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageEntityTest {

  @Test
  void storageNodeRejectsAvailableCapacityGreaterThanTotalCapacity() {
    assertThatThrownBy(
            () ->
                new StorageNode(
                    UUID.randomUUID(),
                    "worker-1",
                    "local-1",
                    "GPU 로컬 저장소",
                    StorageType.LOCAL,
                    StorageNodeStatus.OFFLINE,
                    100L,
                    101L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("availableBytes must be between zero and capacityBytes");
  }

  @Test
  void retentionPolicyRejectsMaximumBelowDefault() {
    assertThatThrownBy(
            () ->
                new RetentionPolicy(
                    UUID.randomUUID(), "invalid-policy", 30, 3, 7, 3, false, false, false, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxRetentionDays must not be less than defaultRetentionDays");
  }
}
