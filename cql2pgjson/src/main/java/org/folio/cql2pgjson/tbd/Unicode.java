package org.folio.cql2pgjson.tbd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provide a regular expression for each character when ignoring none, case and/or diacritics.
 */
public enum Unicode {
  /**
   * Ignores neither case nor diacritics/accents. getEquivalents(ch) returns a regexp for ch.
   */
  IGNORE_NONE {
      @Override
      public String getEquivalents(char ch) {
          return nullHandler(null, ch);
      }
  },
  /**
   * Case ignoring equivalent. The return of getEquivalents(ch)
   * is a regular expression for one character that is ch or ch in different case.
   * @see java.lang.Character#toLowerCase(java.lang.Character)
   * @see java.lang.Character#toUpperCase(java.lang.Character)
   * @see java.lang.Character#toTitleCase(java.lang.Character)
   */
  IGNORE_CASE {
      @Override
      public String getEquivalents(char ch) {
          return nullHandler(UnicodeIgnoreCase.getRegexpMap().get(ch), ch);
      }
  },
  /**
   * Diacritics/accents ignoring equivalent. The return of getEquivalents(ch)
   * is a regular expression for one character that is ch or a character that differs in
   * diacritics/accents only.
   */
  IGNORE_ACCENTS {
      @Override
      public String getEquivalents(char ch) {
          return nullHandler(UnicodeIgnoreAccents.getRegexpMap().get(ch), ch);
      }
  },
  /**
   * Case and diacritics/accents ignoring equivalent. The return of getEquivalents(ch)
   * is a regular expression for one character that is ch or a character that differs in
   * case and/or diacritics/accents only.
   * @see java.lang.Character#toLowerCase(java.lang.Character)
   * @see java.lang.Character#toUpperCase(java.lang.Character)
   * @see java.lang.Character#toTitleCase(java.lang.Character)
   */
  IGNORE_CASE_AND_ACCENTS {
      @Override
      public String getEquivalents(char ch) {
          return nullHandler(UnicodeIgnoreCaseAccents.getRegexpMap().get(ch), ch);
      }
  };

  /**
   * Return all equivalent chars of ch as regular expression.
   * @param ch  the character to return the equivalents for.
   * @return regular expression for all equivalents including ch
   */
  public abstract String getEquivalents(char ch);

  @SuppressWarnings("squid:MethodCyclomaticComplexity")
  private static String nullHandler(String s, char ch) {
    if (s != null) {
      return s;
    }
    switch (ch) {
    case '.':
    case '+':
    case '*':
    case '(':
    case ')':
    case '[':
    case ']':
    case '{':
    case '}':
    case '\\':
      return "\\" + ch;
    default:
      return String.valueOf(ch);
    }
  }

  private static BufferedReader resource(String filename) {
    InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
    if (input == null) {
      throw new IllegalStateException("Resource not found: " + filename);
    }
    InputStreamReader reader = new InputStreamReader(input, UTF_8);
    return new BufferedReader(reader);
  }

  /**
   * Reads the resource filename where each line contains a character, a tabulator
   * and a String (minimum length 1).
   * @param filename  resource file to read
   * @return unmodifiable map that maps each character to the String of that line.
   * @throws IllegalStateException on error
   */
  public static Map<Character,String> readMappingFile(String filename) {
    return readMappingFile(resource(filename));
  }

  /**
   * Reads the resource filename where each line contains a character, a tabulator
   * and a String (minimum length 1).
   * @param in  source to read the file from
   * @return unmodifiable map that maps each character to the String of that line.
   * @throws IllegalStateException on error
   */
  public static Map<Character,String> readMappingFile(BufferedReader in) {
    try {
      Map<Character,String> map = new HashMap<>();
      String line;
      while ((line = in.readLine()) != null) {
        if (line.length() == 0) {
          continue;
        }
        if (line.length() < 3) {
          throw new IllegalStateException("line too short: " + line);
        }
        if (line.charAt(1) != '\t') {
          throw new IllegalStateException("second character must be a tabulator: " + line);
        }
        Character c = line.charAt(0);
        String s = line.substring(2).intern();
        map.put(c,  s);
      }
      return Collections.unmodifiableMap(map);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
