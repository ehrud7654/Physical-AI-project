package com.anssa8bu.ssafy.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.anssa8bu.ssafy.support.container.SharedTestContainers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Spring 컨텍스트 없이 별도의 빈 database에서 Flyway를 직접 실행한다. IntegrationTestBase는 컨텍스트 기동 시 이미
 * migration을 적용하므로 "빈 DB" 시나리오를 검증할 수 없다.
 */
class FlywayMigrationIntegrationTest {
  static final int MIGRATION_COUNT = 8;
  static final int TABLE_COUNT = 28;
  static final List<String> PARTIAL_UNIQUE_INDEXES =
      List.of(
          "uq_users_email_lower",
          "uq_retention_policy_default",
          "uq_training_profiles_one_active",
          "uq_episode_active_purge_job",
          "uq_robot_active_deployment",
          "uq_robot_active_skill",
          "uq_robot_active_execution",
          "ix_outbox_publishable",
          "ix_outbox_expired_processing",
          "ix_outbox_published_cleanup");

  @BeforeAll
  static void startContainers() {
    SharedTestContainers.start();
  }

  @Test
  void emptyDatabaseMigratesFullyAndRerunIsNoop() throws SQLException {
    String url = freshDatabase("mig_full");
    Flyway flyway = flyway(url);

    MigrateResult first = flyway.migrate();
    assertThat(first.success).isTrue();
    assertThat(first.migrationsExecuted).isEqualTo(MIGRATION_COUNT);
    assertSchemaAndSeed(url);

    MigrateResult second = flyway.migrate();
    assertThat(second.migrationsExecuted).isZero();
    flyway.validate();
    assertSchemaAndSeed(url);
  }

  @Test
  void migratingV1FirstThenTheRestProducesSameSchema() throws SQLException {
    String url = freshDatabase("mig_staged");

    MigrateResult v1 = flyway(url, "1").migrate();
    assertThat(v1.migrationsExecuted).isEqualTo(1);
    // V1에는 잘못된 CHECK 두 개가 포함되어 있고 V2/V4/V5가 이를 정정하며 V8이 VLM 도메인을 제거한다
    assertThat(count(url, "select count(*) from training_profiles")).isZero();

    MigrateResult rest = flyway(url).migrate();
    assertThat(rest.migrationsExecuted).isEqualTo(MIGRATION_COUNT - 1);
    assertSchemaAndSeed(url);
  }

  private void assertSchemaAndSeed(String url) throws SQLException {
    assertThat(count(url, "select count(*) from flyway_schema_history where success"))
        .isEqualTo(MIGRATION_COUNT);
    assertThat(
            count(
                url,
                "select count(*) from information_schema.tables"
                    + " where table_schema = 'public' and table_type = 'BASE TABLE'"
                    + " and table_name <> 'flyway_schema_history'"))
        .isEqualTo(TABLE_COUNT);
    for (String index : PARTIAL_UNIQUE_INDEXES) {
      assertThat(count(url, "select count(*) from pg_indexes where indexname = '" + index + "'"))
          .as(index)
          .isOne();
    }
    // seed: 중복 없이 정확히 1회만 적용
    assertThat(count(url, "select count(*) from skills")).isEqualTo(5);
    assertThat(
            count(
                url, "select count(*) from training_profiles where profile_key = 'umi-act-default'"))
        .isOne();
    assertThat(
            count(
                url,
                "select count(*) from retention_policies where name = 'dev-provisional-retention'"))
        .isOne();
    // V8: VLM 도메인 제거 확인
    assertThat(
            count(
                url,
                "select count(*) from information_schema.tables where table_schema = 'public'"
                    + " and table_name in ('skill_destinations','vlm_model_versions','vlm_policies',"
                    + "'vlm_inference_results')"))
        .isZero();
    assertThat(
            count(
                url,
                "select count(*) from information_schema.columns where table_name = 'skills'"
                    + " and column_name = 'needs_vlm'"))
        .isZero();
    // V2/V4가 제거한 잘못된 config_sha256 CHECK는 정규식 CHECK 하나만 남는다
    assertThat(
            count(
                url,
                "select count(*) from pg_constraint where conrelid = 'training_profiles'::regclass"
                    + " and contype = 'c' and pg_get_constraintdef(oid) like '%config_sha256%'"))
        .isOne();
    // V5: 구 status CHECK 제거, 신규 CHECK 4개
    assertThat(
            count(
                url,
                "select count(*) from pg_constraint where conrelid = 'outbox_events'::regclass"
                    + " and conname in ('ck_outbox_status','ck_outbox_claim_consistency',"
                    + "'ck_outbox_attempt','ck_outbox_sequence')"))
        .isEqualTo(4);
    assertThat(
            count(
                url,
                "select count(*) from pg_constraint where conname = 'outbox_events_status_check'"))
        .isZero();
  }

  private static Flyway flyway(String url) {
    return flyway(url, null);
  }

  private static Flyway flyway(String url, String target) {
    var config =
        Flyway.configure()
            .dataSource(url, SharedTestContainers.username(), SharedTestContainers.password())
            .locations("classpath:db/migration");
    if (target != null) config = config.target(target);
    return config.load();
  }

  /** 공유 컨테이너 안에 이름이 같은 database를 지우고 새로 만든 뒤 그 JDBC URL을 돌려준다. */
  private static String freshDatabase(String name) throws SQLException {
    try (Connection c = admin();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + name);
      s.execute("CREATE DATABASE " + name);
    }
    return SharedTestContainers.jdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        SharedTestContainers.jdbcUrl(),
        SharedTestContainers.username(),
        SharedTestContainers.password());
  }

  private static long count(String url, String sql) throws SQLException {
    try (Connection c =
            DriverManager.getConnection(
                url, SharedTestContainers.username(), SharedTestContainers.password());
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
