package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.folio.cql2pgjson.tbd.Unicode;
import org.folio.cql2pgjson.tbd.UnicodeIgnoreCase;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class UnicodeIgnoreCaseTest {

  private static String [] tests = { "", "a", "A", "b", "B", "aa", "AA", "ab", "bb" };

  public static void match(Character c, String test, boolean match) {
    String regexp = Unicode.IGNORE_CASE.getEquivalents(c);
    boolean matching = test.matches("^" + regexp + "$");
    String title = "c=" + c + " test=" + test + " match=" + match + " regexp=" + regexp;
    assertTrue(title, matching == match);
  }

  @Test
  @Parameters({
      "a,A,A,a",
      "b,B,b,B",
      "z,Z,z,Z",
      "ä,Ä,ä,Ä",
      "ß,ẞ,ß,ẞ"  // LATIN SMALL LETTER SHARP S, LATIN CAPITAL LETTER SHARP S
    })
  public void match(Character c1, Character c2, String ... expected) {
    // the regexp of each c1 and c2 must match each expected string
    for (String test : expected) {
      match(c1, test, true);
      match(c2, test, true);
    }
    // the regexp of each c1 and c2 must not match any not expected string
    for (String test : tests) {
      if (Arrays.asList(expected).contains(test)) {
        continue;
      }
      match(c1, test, false);
      match(c2, test, false);
    }
  }

  @Test
  public void utilityClass() {
    Util.assertUtilityClass(UnicodeIgnoreCase.class);
  }
}
