package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class UnicodeIgnoreDiacriticsTest {

  private static String [] tests = { "",
      "a", "A", "ä", "Ä",
      "o", "ö", "ø", "O", "Ö", "Ø",
      "b", "B",
      "aä", "ÄÄ",
      "ab", "bb", "ob", "öb", "øb", "Ob", "Öb", "Øb"
  };

  public static void match(Character c, String test, boolean match) {
    String regexp = Unicode.IGNORE_DIACRITICS.getEquivalents(c);
    boolean matching = test.matches("^" + regexp + "$");
    String title = "c=" + c + " test=" + test + " match=" + match + " regexp=" + regexp;
    assertTrue(title, matching == match);
  }

  @Test
  @Parameters({
    "a,a,ä",
    "ä,a,ä",
    "A,A,Ä",
    "Ä,A,Ä",
    "o,o,ö,ø",
    "ö,o,ö,ø",
    "ø,o,ö,ø",
    "O,O,Ö,Ø",
    "Ö,O,Ö,Ø",
    "Ø,O,Ö,Ø",
    "b,b",
    "B,B",
    "z,z",
    "Z,Z",
    "ß,ß",  // LATIN SMALL LETTER SHARP S
    "ẞ,ẞ"   // LATIN CAPITAL LETTER SHARP S
  })
  public void match(Character c, String ... expected) {
    for (String test : expected) {
      match(c, test, true);
    }
    for (String test : tests) {
      if (Arrays.asList(expected).contains(test)) {
        continue;
      }
      match(c, test, false);
    }
  }

  @Test
  public void utilityClass() {
    Util.assertUtilityClass(UnicodeIgnoreDiacritics.class);
  }
}
