package com.anssa8bu.ssafy.common.redis;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisKeyGeneratorTest {
  @Test
  void createsNamespacedKeys() {
    var g = new RedisKeyGenerator("local", "umi");
    UUID id = UUID.randomUUID();
    assertThat(g.trainingLease(id)).isEqualTo("local:umi:training-lease:" + id);
    assertThat(g.activeTrainingProfile()).isEqualTo("local:umi:training-profile:active");
  }

  @Test
  void rejectsUnsafeSegments() {
    assertThatThrownBy(() -> new RedisKeyGenerator("../prod", "umi"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
