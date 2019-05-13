package org.folio.cql2pgjson.tbd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.folio.cql2pgjson.tbd.Unicode;
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

  @Test
  public void ioException() throws IOException {
    BufferedReader in = new BufferedReader(new StringReader(""));
    in.close();
    expectIllegalStateException("IOException: Stream closed");
    Unicode.readMappingFile(in);
  }
}
