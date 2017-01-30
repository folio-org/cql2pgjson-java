package org.z3950.zing.cql.cql2pgjson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calculating the mappings takes about 2 seconds. This should not be redone whenever one of the Unicode
 * class is loaded, instead it is precalculated, written to some resource file and loaded by the static
 * initialization of the classes (UnicodeIgnoreCase, ...). The static initialization is invoked only when
 * the class is actually actually invoked.
 */
public class UnicodeMapFileGenerator {
  /** iterates over Character.isSurrogate(ch) */
  private static class NonSurrogates implements Iterator<Character>
  {
    private int i = Character.MIN_CODE_POINT;

    @Override
    public Character next() {
      if (! hasNext()) {
        throw new NoSuchElementException();
      }
      char [] c = Character.toChars(i++);
      return c[0];
    }

    @Override
    public boolean hasNext() {
      return i < Character.MIN_SUPPLEMENTARY_CODE_POINT;
    }
  }

  private static Iterable<Character> nonSurrogates = NonSurrogates::new;
  private static Pattern diacritics = Pattern.compile("[\\p{InCombiningDiacriticalMarks}\uFE20\uFE21]+");

  private UnicodeMapFileGenerator() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  private static String removeDiacritics(String s) {
    String plain = Normalizer.normalize(s, Normalizer.Form.NFKD);
    if ("ø".equals(plain)) {
      plain = "o";
    } else if ("Ø".equals(plain)) {
      plain = "O";
    }
    return diacritics.matcher(plain).replaceAll("");
  }

  /**
   * Invoke map.get(c).add(value), insert a new empty HashSet before if needed.
   *
   * @return true iff the set does not contain the value before
   */
  private static boolean add(Map<Character,Set<String>> map, Character c, String value) {
    Set<String> set = map.get(c);
    if (set == null) {
      set = new HashSet<>();
      map.put(c,  set);
    }
    return set.add(value);
  }

  /**
   * For each value that is one character long add each value in its set.
   */
  private static void addValueValue(Map<Character,Set<String>> map, String ... values) {
    for (String a : values) {
      if (a.length() != 1) {
        continue;
      }
      Character aChar = a.charAt(0);
      for (String b : values) {
        add(map, aChar, b);
      }
    }
  }

  /**
   * For each value of all sets that is a Character invoke add(map, c, value).
   * @return true iff any set has been changed
   */
  private static boolean addIndirectEquivalences(Map<Character,Set<String>> map) {
    boolean changed = false;

    for (Map.Entry<Character, Set<String>> entry: map.entrySet()) {
      for (String s : entry.getValue()) {
        if (s.length() != 1) {
          continue;
        }
        Character c = s.charAt(0);
        for (String value : entry.getValue()) {
          changed |= add(map, c, value);
        }
      }
    }

    return changed;
  }

  /**
   * If s is a single backslash mask it by prepending a backslash, otherwise return s unchanged.
   * @param s source String
   * @return s masked String
   */
  private static String maskBackslash(String s) {
    if ("\\".equals(s)) {
      return "\\\\";
    }
    return s;
  }

  private static void createRegexp(Map<Character,Set<String>> mapSet, Map<Character,String> mapRegexp) {
    for (Map.Entry<Character,Set<String>> entry : mapSet.entrySet()) {
      Character c = entry.getKey();
      Set<String> values = entry.getValue();
      int maxLength         = values.stream().mapToInt(String::length).max().orElse(0);
      Stream<String> sorted = values.stream().map(UnicodeMapFileGenerator::maskBackslash).sorted();
      String regexp;
      if (maxLength == 1) {
        regexp = "[" + sorted.collect(Collectors.joining()) + "]";
      } else {
        regexp = "(?:" + sorted.collect(Collectors.joining("|")) + ")";
      }
      mapRegexp.put(c, regexp);
    }
  }

  @FunctionalInterface
  private interface Equivalents {
    String [] equivalents(Character c);
  }

  private static String [] ignoreCase(Character c) {
    String s = c.toString();
    String upper =     s.toUpperCase(Locale.ROOT);
    String lower = upper.toLowerCase(Locale.ROOT);
    String title = Character.toString(Character.toTitleCase(c));
    if (s.equals(upper) && s.equals(lower) && s.equals(title)) {
      return new String [] {};
    }
    return new String [] { s, lower, upper, title };
  }

  private static String [] ignoreDiacritics(Character c) {
    String s = c.toString();
    String plain = removeDiacritics(s);
    if (s.equals(plain)) {
      return new String [] {};
    }
    return new String [] { s, plain };
  }

  private static String [] ignoreCaseDiacritics(Character c) {
    String s = c.toString();
    String upper = removeDiacritics(    s.toUpperCase(Locale.ROOT));
    String lower = removeDiacritics(upper.toLowerCase(Locale.ROOT));
    String title = removeDiacritics(Character.toString(Character.toTitleCase(c)));
    if (s.equals(upper) && s.equals(lower) && s.equals(title)) {
      return new String [] {};
    }
    return new String [] { s, lower, upper, title };
  }

  private static Map<Character,String> regexpMap(Equivalents equivalents) {
    Map<Character,Set<String>> ignoreSet = new HashMap<>();
    for (Character c : nonSurrogates) {
      addValueValue(ignoreSet, equivalents.equivalents(c));
    }

    // add indirect equivalences.
    // Example:
    // ß "\u00DF" 'LATIN SMALL LETTER SHARP S'
    // ẞ "\u1E9E" 'LATIN CAPITAL LETTER SHARP S'
    // "ß".toUpperCase() = "SS"
    // "ẞ".toLowerCase() = "ß", need to add missing "SS" to this
    addIndirectEquivalences(ignoreSet);

    Map<Character,String> regexp = new HashMap<>(ignoreSet.size());
    createRegexp(ignoreSet, regexp);
    return regexp;
  }

  public static void generateMapFile(String file, Equivalents equivalents) throws IOException {
    Path path = Paths.get(file);
    Files.createDirectories(path.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      Map<Character,String> map = regexpMap(equivalents);
      for (Character c : nonSurrogates) {
        if (map.containsKey(c)) {
          writer.write(c + "\t" + map.get(c) + "\n");
        }
      }
    }
  }

  public static void main(String [] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("1 argument (output dir) expected, " + args.length + " arguments found.");
    }
    generateMapFile(args[0] + "/UnicodeIgnoreCase",           UnicodeMapFileGenerator::ignoreCase);
    generateMapFile(args[0] + "/UnicodeIgnoreDiacritics",     UnicodeMapFileGenerator::ignoreDiacritics);
    generateMapFile(args[0] + "/UnicodeIgnoreCaseDiacritics", UnicodeMapFileGenerator::ignoreCaseDiacritics);
  }
}
