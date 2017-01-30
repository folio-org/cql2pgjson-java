package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;

import org.junit.Test;

public class UnicodeMapFileGeneratorTest {
  @Test (expected = IllegalArgumentException.class)
  public void illegalArgumentException() throws IOException {
    UnicodeMapFileGenerator.main(new String [] {});
  }

  @Test
  public void targetTest() throws IOException {
    UnicodeMapFileGenerator.main(new String [] {"target/test-generator/"});
  }

  @Test
  public void utilityClass() {
    Util.assertUtilityClass(UnicodeMapFileGenerator.class);
  }
}
