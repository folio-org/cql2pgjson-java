package org.folio.cql2pgjson.tbd;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.folio.cql2pgjson.tbd.Unicode;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class UnicodeIgnoreNoneTest {
  private static String [] tests = { "",
      "a", "A", "ä", "Ä",
      "o", "ö", "ø", "O", "Ö", "Ø",
      "b", "B",
      "aä", "ÄÄ",
      "ab", "bb", "ob", "öb", "øb", "Ob", "Öb", "Øb"
  };

  public static void match(Character c, String test, boolean match) {
    String regexp = Unicode.IGNORE_NONE.getEquivalents(c);
    boolean matching = test.matches("^" + regexp + "$");
    String title = "c=" + c + " test=" + test + " match=" + match + " regexp=" + regexp;
    assertTrue(title, matching == match);
  }

  @Parameters({
    "a,a",
    "ä,ä",
    "A,A",
    "Ä,Ä",
    "o,o",
    "ö,ö",
    "ø,ø",
    "O,O",
    "Ö,Ö",
    "Ø,Ø",
    "b,b",
    "B,B",
    "z,z",
    "Z,Z",
    "ß,ß",  // LATIN SMALL LETTER SHARP S
    "ẞ,ẞ",  // LATIN CAPITAL LETTER SHARP S
    ".,.",
    "+,+",
    "(,(",
    "),)",
    "[,[",
    "],]",
    "{,{",
    "},}",
  })
  @Test
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
}
