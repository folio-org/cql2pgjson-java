package org.folio.cql2pgjson.tbd;

import java.util.Map;

/**
 * Provides regular expressions for matching Unicode characters ignoring accents/diacritics.
 */
public final class UnicodeIgnoreAccents {
  private static Map<Character,String> unmodifiableMap = Unicode.readMappingFile("UnicodeIgnoreAccents");

  private UnicodeIgnoreAccents() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  /**
   * For each Character c the map provides a regexp that matches any
   * String that is a accents/diacritics ignoring equivalent to c including c.
   * The map returns null if there is no other equivalent for c.
   * The map is unmodifiable.
   * @return  the map
   */
  public static Map<Character,String> getRegexpMap() {
    return unmodifiableMap;
  }
}
