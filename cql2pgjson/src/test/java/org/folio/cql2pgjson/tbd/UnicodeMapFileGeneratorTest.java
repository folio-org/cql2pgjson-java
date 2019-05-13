package org.folio.cql2pgjson.tbd;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.folio.cql2pgjson.tbd.UnicodeMapFileGenerator;
import org.junit.Test;
import org.z3950.zing.cql.cql2pgjson.Util;

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

  @Test(expected=NoSuchElementException.class)
  public void nonSurrogatesThrowsNoSuchElementException() {
    Iterator<Character> it = UnicodeMapFileGenerator.nonSurrogates.iterator();
    try {
       while (it.hasNext()) {
         it.next();
       }
    } catch (Exception e) {
      fail(e.toString());
    }
    it.next();
  }
}
