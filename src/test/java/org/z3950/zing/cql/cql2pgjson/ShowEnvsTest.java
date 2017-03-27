package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShowEnvsTest {
  @Test
  public void showEnvs() {
    System.getenv().keySet().stream().sorted().forEach(key -> System.out.println("showEnvs key=" + key));
    assertTrue(true);
  }
}
