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
 * Spring 컨텍스트 없이 별도의 빈 database에서 Flyway를 직접 실행한다. IntegrationTestBase는 컨텍스트 기동 시 이미 migration을
 * 적용하므로 "빈 DB" 시나리오를 검증할 수 없다.
 */
class FlywayMigrationIntegrationTest {
  static final int MIGRATION_COUNT = 10;
  static final int TABLE_COUNT = 29;
  static final List<String> PARTIAL_UNIQUE_INDEXES =
      List.of(
          "uq_users_email_lower",
          "uq_retention_policy_default",
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
    assertThat(count(url, "select count(*) from skills")).isEqualTo(2);
    assertThat(
            count(
                url,
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'training_profiles'"))
        .isZero();
    assertThat(
            count(
                url,
                "select count(*) from retention_policies where name = 'dev-provisional-retention'"))
        .isOne();
    // 통합 baseline에는 폐기된 VLM 도메인이 존재하지 않는다.
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
    // FE 연동용 사용자 하위 Robot, 학습 결과와 Task 구성이 정확히 연결된다.
    assertThat(
            count(
                url,
                "select count(*) from users where id ="
                    + " '50000000-0000-4000-8000-000000000001'::uuid"
                    + " and email = 'admin01@ssafy.local' and status = 'ACTIVE'"))
        .isOne();
    assertThat(
            count(
                url,
                "select count(*) from robots where owner_id ="
                    + " '50000000-0000-4000-8000-000000000001'::uuid"))
        .isOne();
    assertThat(
            count(
                url,
                "select count(*) from training_jobs tj"
                    + " join skill_versions sv on sv.id = tj.result_skill_version_id"
                    + " where tj.owner_id = '50000000-0000-4000-8000-000000000001'::uuid"
                    + " and tj.status = 'SUCCEEDED' and sv.status = 'READY' and sv.gate_passed"))
        .isEqualTo(2);
    assertThat(
            count(
                url,
                "select count(*) from tasks t"
                    + " join task_versions tv on tv.task_id = t.id"
                    + " join task_steps ts on ts.task_version_id = tv.id"
                    + " where t.owner_id = '50000000-0000-4000-8000-000000000001'::uuid"
                    + " and t.task_key = 'raise_blue_flag'"))
        .isEqualTo(2);
    // V4 removes trainer-owned configuration from the EC2 orchestration schema.
    assertThat(
            count(
                url,
                "select count(*) from information_schema.columns where table_schema = 'public' and table_name in ('training_jobs', 'skill_versions') and column_name in ('training_profile_id', 'training_config_snapshot', 'training_config_sha256')"))
        .isZero(); // Outbox 최종 CHECK 4개만 존재한다.
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
