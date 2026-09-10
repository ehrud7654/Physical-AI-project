package com.anssa8bu.ssafy.support.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * 예외 발생 여부만이 아니라 PostgreSQL SQLSTATE와 constraint 이름까지 확인한다. Hibernate/Spring이 어떤 예외로
 * 감싸든 cause 체인에서 {@link SQLException}을 찾는다.
 */
public final class PostgresConstraintAssertions {
  public static final String NOT_NULL = "23502";
  public static final String FOREIGN_KEY = "23503";
  public static final String UNIQUE = "23505";
  public static final String CHECK = "23514";
  public static final String INVALID_TEXT = "22P02";

  private PostgresConstraintAssertions() {}

  public static void assertUniqueViolation(String constraint, ThrowingCallable action) {
    assertViolates(UNIQUE, constraint, action);
  }

  public static void assertCheckViolation(String constraint, ThrowingCallable action) {
    assertViolates(CHECK, constraint, action);
  }

  public static void assertForeignKeyViolation(String constraint, ThrowingCallable action) {
    assertViolates(FOREIGN_KEY, constraint, action);
  }

  public static void assertViolates(String sqlState, String constraint, ThrowingCallable action) {
    SQLException sql = assertSqlState(sqlState, action);
    // PostgreSQL 메시지 예: violates unique constraint "uq_users_email_lower"
    assertThat(sql.getMessage()).as("constraint name").contains("\"" + constraint + "\"");
  }

  public static SQLException assertSqlState(String sqlState, ThrowingCallable action) {
    Throwable thrown = catchThrowable(action);
    assertThat(thrown).as("expected SQLSTATE %s but nothing was thrown", sqlState).isNotNull();
    SQLException sql = sqlException(thrown);
    assertThat(sql == null).as("no SQLException in cause chain of %s", thrown).isFalse();
    assertThat(sql.getSQLState()).as("SQLSTATE of %s", sql.getMessage()).isEqualTo(sqlState);
    return sql;
  }

  private static SQLException sqlException(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
      if (c instanceof SQLException sql) return sql;
    }
    return null;
  }
}
