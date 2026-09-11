package com.anssa8bu.ssafy.common.redis;

import static org.assertj.core.api.Assertions.*;

import com.anssa8bu.ssafy.common.redis.service.CacheAsideService;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class CacheAsideServiceTest {
  final CacheAsideService service = new CacheAsideService();

  @Test
  void fallsBackToDatabaseWhenRedisIsDown() {
    AtomicInteger db = new AtomicInteger();
    String value =
        service.get(
            () -> {
              throw new RuntimeException("redis unavailable");
            },
            () -> {
              db.incrementAndGet();
              return "db";
            },
            v -> {
              throw new RuntimeException("still unavailable");
            });
    assertThat(value).isEqualTo("db");
    assertThat(db).hasValue(1);
  }

  @Test
  void cacheMissLoadsDatabaseAndPopulates() {
    AtomicReference<String> populated = new AtomicReference<>();
    String value = service.get(Optional::<String>empty, () -> "db", populated::set);
    assertThat(value).isEqualTo("db");
    assertThat(populated).hasValue("db");
  }
}
