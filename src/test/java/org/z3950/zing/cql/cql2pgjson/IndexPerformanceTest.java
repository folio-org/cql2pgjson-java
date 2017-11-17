package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.fail;

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
  @BeforeClass
  public static void createData() {
    Assume.assumeTrue("TEST_PERFORMANCE=yes", "yes".equals(System.getenv("TEST_PERFORMANCE")));
    setupDatabase();
    runSql(
        "CREATE EXTENSION IF NOT EXISTS pgcrypto;",
        "DROP TABLE IF EXISTS config_data;",
        "CREATE TABLE config_data (",
        "   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),",
        "   jsonb jsonb NOT NULL);",
        "INSERT INTO config_data",
        "  SELECT id, jsonb_build_object('id',id, 'module','circulation', 'configName','loans',",
        "    'code',id2, 'description',description, 'value',value, 'default',true,",
        "    'enabled',true)",
        "  FROM (  select gen_random_uuid() AS id,",
        "          generate_series(1, 1000004) AS id2,",
        "          (md5(random()::text) || ' 123445 4ewfsdfqw' || now()) AS description,",
        "          (md5(random()::text) || now()) AS value",
        "       ) AS alias;");
  }

  private void in100ms(String where) {
    long t1 = System.currentTimeMillis();
    runSql("SELECT * FROM config_data " + where);
    long t2 = System.currentTimeMillis();
    long ms = t2 - t1;
    System.out.println(String.format("%6d ms %s", ms, where));
    final long MAX = 100;
    if (ms > MAX) {
      fail("Expected at most " + MAX + " ms, but it runs " + ms + " ms: " + where);
    }
  }

  @Test
  @Parameters({
    "jsonb->>'value'",
    "jsonb->'value'",
    "(jsonb->'value')::text",
    "lower(jsonb->>'value')",
    "lower((jsonb->'value')::text)",
  })
  public void valueIndex(String index) {
    runSqlStatement("DROP INDEX IF EXISTS idx_value;");
    runSqlStatement("CREATE INDEX idx_value ON config_data ((" + index + "))");
    in100ms("WHERE TRUE LIMIT 30;");
    in100ms("WHERE TRUE ORDER BY " + index + " ASC  LIMIT 30;");
    in100ms("WHERE TRUE ORDER BY " + index + " DESC LIMIT 30;");
  }
}
