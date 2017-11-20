package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.sql.Statement;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

/**
 * Performance test of index usage.
 * <p>
 * Only runs if environment variable TEST_PERFORMANCE=yes, for example <br>
 * TEST_PERFORMANCE=yes mvn test
 */
@RunWith(JUnitParamsRunner.class)
public class IndexPerformanceTest extends DatabaseTestBase {
  private static String valueJsonbToFind = "'\"a1b2c3d4e5f6 xxxx\"'";
  private static String valueStringToFind = valueJsonbToFind.replace("\"", "");

  @BeforeClass
  public static void beforeClass() {
    Assume.assumeTrue("TEST_PERFORMANCE=yes", "yes".equals(System.getenv("TEST_PERFORMANCE")));
    setupDatabase();
    runSqlFile("indexPerformanceTest.sql");
  }

  @AfterClass
  public static void afterClass() {
    closeDatabase();
  }

  static class Analyse {
    String msg;
    /** execution time in ms */
    float executionTime;
    public Analyse(String msg, float executionTime) {
      this.msg = msg;
      this.executionTime = executionTime;
    }
  }

  private Analyse analyse(String sql) {
    try (Statement statement = conn.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new SQLRuntimeException(sql, e);
    }
    return new Analyse("", 0);
  }

  private void in100ms(String where) {
    long t1 = System.currentTimeMillis();
    String analyse = explainAnalyseSql("SELECT * FROM config_data " + where);
    long t2 = System.currentTimeMillis();
    long ms = t2 - t1;
    System.out.println(String.format("%6d ms %s\n%s", ms, where, analyse));
    final long MAX = 100;
    if (ms > MAX) {
      fail("Expected at most " + MAX + " ms, but it runs " + ms + " ms: " + where);
    }
    if (! analyse.contains(" idx_value ")) {
      fail("Query plan does not use idx_value: " + where);
    }
  }

  private void in100msAfterDry(String where) {
    runSqlStatement("SELECT * FROM config_data " + where);  // dry run
    in100ms(where);  // actual run
  }

  @Test
  @Parameters({
    "jsonb->'value'",
    "jsonb->>'value'",
    "(jsonb->'value')::text",
    "lower(jsonb->>'value')",
    "lower((jsonb->'value')::text)",
  })
  public void valueIndex(String index) {
    runSqlStatement("DROP INDEX IF EXISTS idx_value;");
    runSqlStatement("CREATE INDEX idx_value ON config_data ((" + index + "))");
    in100ms("WHERE TRUE ORDER BY " + index + " ASC  LIMIT 30;");
    in100ms("WHERE TRUE ORDER BY " + index + " DESC LIMIT 30;");
    String match = valueStringToFind;
    if (index.contains("->'")) {
      match = valueJsonbToFind;
    }
    in100ms("WHERE " + index + " = " + match);
  }

  private void like(String index, String sort) {
    String match = "'\"a1%\"'";
    if (index.contains("->>")) {
      match = match.replace("\"", "");
    }
    in100msAfterDry("WHERE lower(f_unaccent(" + index + ")) LIKE " + match
        +       " ORDER BY lower(f_unaccent(" + index + ")) " + sort + ", " + index + sort + "LIMIT 30;");
  }

  @Test
  @Parameters({
    "jsonb->>'value'",
    "(jsonb->'value')::text",
  })
  public void like(String index) {
    runSqlStatement("DROP INDEX IF EXISTS idx_value;");
    String finalIndex = "lower(f_unaccent(" + index + "))";
    runSqlStatement("CREATE INDEX idx_value ON config_data ((" + finalIndex + ") text_pattern_ops);");
    String [] sorts = { " ASC  ", " DESC " };
    for (String sort : sorts) {
      like(index, sort);
    }
  }
}
