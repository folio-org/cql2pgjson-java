package org.z3950.zing.cql.cql2pgjson;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UnicodeTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  /**
   * Set {@link #exception} that an IllegalStateException is expected with the message substring.
   * @param substring  message substring
   */
  public void expectIllegalStateException(String substring) {
    exception.expect(IllegalStateException.class);
    exception.expectMessage(substring);
  }

  @Test
  public void a() {
    expectIllegalStateException("too short");
    Unicode.readMappingFile("mapping-a");
  }

  @Test
  public void aa() {
    expectIllegalStateException("tabulator");
    Unicode.readMappingFile("mapping-aaa");
  }

  @Test
  public void fileNotFound() {
    expectIllegalStateException("not found");
    Unicode.readMappingFile("mapping-not-exists");
  }
}