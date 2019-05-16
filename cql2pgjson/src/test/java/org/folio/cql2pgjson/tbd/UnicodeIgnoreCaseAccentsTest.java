package org.folio.cql2pgjson.tbd;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.folio.cql2pgjson.tbd.Unicode;
import org.folio.cql2pgjson.tbd.UnicodeIgnoreCaseAccents;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.z3950.zing.cql.cql2pgjson.Util;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class UnicodeIgnoreCaseAccentsTest {

  private static String [] tests = { "",
      "a", "A", "ä", "Ä",
      "o", "ö", "ø", "O", "Ö", "Ø",
      "b", "B",
      "aä", "ÄÄ",
      "ab", "bb", "ob", "öb", "øb", "Ob", "Öb", "Øb"
  };

  public static void match(Character c, String test, boolean match) {
    String regexp = Unicode.IGNORE_CASE_AND_ACCENTS.getEquivalents(c);
    boolean matching = test.matches("^" + regexp + "$");
    String title = "c=" + c + " test=" + test + " match=" + match + " regexp=" + regexp;
    assertTrue(title, matching == match);
  }

  @Test
  @Parameters({
    "a,A,a,ä,A,Ä",
    "ä,Ä,a,ä,A,Ä",
    "o,O,o,ö,ø,O,Ö,Ø",
    "ö,Ö,o,ö,ø,O,Ö,Ø",
    "ø,Ø,o,ö,ø,O,Ö,Ø",
    "b,B,b,B",
    "z,Z,z,Z",
    "ß,ẞ,ß,ẞ",  // LATIN SMALL LETTER SHARP S, LATIN CAPITAL LETTER SHARP S
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
    Util.assertUtilityClass(UnicodeIgnoreCaseAccents.class);
  }
}
