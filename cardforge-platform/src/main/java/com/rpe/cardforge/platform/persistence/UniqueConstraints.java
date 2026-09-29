package com.rpe.cardforge.platform.persistence;

import java.sql.SQLException;

/** Identifica a violação de uma constraint única específica, sem tratar conflitos genéricos. */
public final class UniqueConstraints {

  private static final String UNIQUE_VIOLATION = "23505";

  private UniqueConstraints() {}

  public static boolean isViolation(Throwable error, String constraintName) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql
          && UNIQUE_VIOLATION.equals(sql.getSQLState())
          && sql.getMessage() != null
          && sql.getMessage().contains("\"" + constraintName + "\"")) {
        return true;
      }
      if (t.getCause() == t) {
        return false;
      }
    }
    return false;
  }
}
