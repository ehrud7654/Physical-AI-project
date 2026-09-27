package com.anssa8bu.ssafy.support.container;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 이미 실행 중인 EC2 개발용 PostgreSQL 17 · Redis 7 컨테이너의 접속 정보를 제공한다.
 *
 * <p>이 클래스는 Docker 컨테이너를 새로 생성하지 않는다. 테스트 실행 스크립트가 {@code TEST_POSTGRES_URL / TEST_POSTGRES_USER /
 * TEST_POSTGRES_PASSWORD}를 주입하고, Redis 접속 정보는 {@code TEST_REDIS_HOST / TEST_REDIS_PORT}로 재정의할 수 있다.
 */
public final class SharedTestContainers {
  private static final String POSTGRES_URL = requiredEnvironmentVariable("TEST_POSTGRES_URL");
  private static final String POSTGRES_USER = requiredEnvironmentVariable("TEST_POSTGRES_USER");
  private static final String POSTGRES_PASSWORD =
      requiredEnvironmentVariable("TEST_POSTGRES_PASSWORD");
  private static final String REDIS_HOST =
      System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1");
  private static final int REDIS_PORT =
      Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));

  private SharedTestContainers() {}

  public static void start() {}

  public static String jdbcUrl() {
    return POSTGRES_URL;
  }

  public static String username() {
    return POSTGRES_USER;
  }

  public static String password() {
    return POSTGRES_PASSWORD;
  }

  public static String redisHost() {
    return REDIS_HOST;
  }

  public static int redisPort() {
    return REDIS_PORT;
  }

  /** {@code @DynamicPropertySource}에서 호출해 Spring datasource/redis 속성을 연결한다. */
  public static void register(DynamicPropertyRegistry registry) {
    start();
    registry.add("spring.datasource.url", SharedTestContainers::jdbcUrl);
    registry.add("spring.datasource.username", SharedTestContainers::username);
    registry.add("spring.datasource.password", SharedTestContainers::password);
    registry.add("spring.data.redis.host", SharedTestContainers::redisHost);
    registry.add("spring.data.redis.port", SharedTestContainers::redisPort);
  }

  private static String requiredEnvironmentVariable(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " 환경변수가 필요합니다.");
    }
    return value;
  }
}
