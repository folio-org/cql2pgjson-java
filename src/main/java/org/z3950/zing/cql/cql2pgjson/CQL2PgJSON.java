package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNotNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CQL2PgJSON {
  /*
   * Contextual Query Language (CQL) Specification:
   * https://www.loc.gov/standards/sru/cql/spec.html
   * https://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part5-cql/searchRetrieve-v1.0-os-part5-cql.html
   */

  /**
   * Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   * Must conform to SQL identifier requirements (characters, not a keyword), or properly
   * quoted using double quotes.
   */
  private String jsonField;
  /** JSON schema of jsonb field as object tree */
  private Object schema;

  /**
   * Default index names to be used for cql.serverChoice.
   * May be empty, but not null. Must not contain null, names must not contain double quote or single quote.
   */
  private List<String> serverChoiceIndexes = Collections.emptyList();

  private enum CqlSort {
    ASCENDING, DESCENDING;
  }

  private enum CqlCase {
    IGNORE_CASE, RESPECT_CASE;
  }

  private enum CqlAccents {
    IGNORE_ACCENTS, RESPECT_ACCENTS;
  }

  private enum CqlMasking {
    MASKED, UNMASKED, SUBSTRING, REGEXP;
  }

  private class CqlModifiers {
    CqlSort    cqlSort    = CqlSort   .ASCENDING;
    CqlCase    cqlCase    = CqlCase   .IGNORE_CASE;
    CqlAccents cqlAccents = CqlAccents.IGNORE_ACCENTS;
    CqlMasking cqlMasking = CqlMasking.MASKED;

    public CqlModifiers(CQLTermNode node) {
      readModifiers(node.getRelation().getModifiers());
    }

    public CqlModifiers(ModifierSet modifierSet) {
      readModifiers(modifierSet.getModifiers());
    }

    /**
     * Read the modifiers and write the last for each enum into the enum variable.
     * Default is ascending, ignoreCase, ignoreAccents and masked.
     *
     * @param modifiers  where to read from
     */
    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public final void readModifiers(List<Modifier> modifiers) {
      for (Modifier m : modifiers) {
        switch (m.getType()) {
        case "sort.ascending" : cqlSort    = CqlSort   .ASCENDING;
        break;
        case "sort.descending": cqlSort    = CqlSort   .DESCENDING;
        break;
        case "ignorecase"     : cqlCase    = CqlCase   .IGNORE_CASE;
        break;
        case "respectcase"    : cqlCase    = CqlCase   .RESPECT_CASE;
        break;
        case "ignoreaccents"  : cqlAccents = CqlAccents.IGNORE_ACCENTS;
        break;
        case "respectaccents" : cqlAccents = CqlAccents.RESPECT_ACCENTS;
        break;
        case "masked"         : cqlMasking = CqlMasking.MASKED;
        break;
        case "unmasked"       : cqlMasking = CqlMasking.UNMASKED;
        break;
        case "substring"      : cqlMasking = CqlMasking.SUBSTRING;
        break;
        case "regexp"         : cqlMasking = CqlMasking.REGEXP;
        break;
        default:
          // ignore
        }
      }
    }
  }

  /** includes unicode characters */
  private static final String WORD_CHARACTER_REGEXP = "[^[:punct:][:space:]]";

  /**
   * Create an instance for the specified schema.
   *
   * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
   *   quoted using double quotes.
   */
  public CQL2PgJSON(String field) {
    if (field == null || field.trim().isEmpty()) {
      throw new IllegalArgumentException("field (containing tableName) must not be empty");
    }
    this.jsonField = field;
  }

  /**
   * Create an instance for the specified schema.
   *
   * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
   *   quoted using double quotes.
   * @param schema JSON String representing the schema of the field the CQL queries against.
   * @throws IOException if the JSON structure is invalid
   */
  public CQL2PgJSON(String field, String schema) throws IOException {
    this(field);
    setSchema(schema);
  }

  /**
   * Create an instance for the specified schema.
   *
   * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
   *   quoted using double quotes.
   * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
   *                                  names must not contain double quote or single quote.
   * @throws IOException if the JSON structure is invalid
   */
  public CQL2PgJSON(String field, List<String> serverChoiceIndexes) throws IOException {
    this(field);
    setServerChoiceIndexes(serverChoiceIndexes);
  }

  /**
   * Create an instance for the specified schema.
   *
   * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
   *   quoted using double quotes.
   * @param schema JSON String representing the schema of the field the CQL queries against.
   * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
   *                                  names must not contain double quote or single quote.
   * @throws IOException if the JSON structure is invalid
   */
  public CQL2PgJSON(String field, String schema, List<String> serverChoiceIndexes) throws IOException {
    this(field);
    setSchema(schema);
    setServerChoiceIndexes(serverChoiceIndexes);
  }

  /**
   * Set the schema of the field.
   * @param schema  JSON String representing the schema of the field the CQL queries against.
   * @throws IOException if the JSON structure is invalid
   */
  private void setSchema(String schema) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    this.schema = mapper.readValue(schema, Object.class);
  }

  /**
   * Set the index names (field names) for cql.serverChoice.
   * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
   *                                  names must not contain double quote or single quote.
   */
  public void setServerChoiceIndexes(List<String> serverChoiceIndexes) {
    if (serverChoiceIndexes == null) {
      this.serverChoiceIndexes = Collections.emptyList();
      return;
    }
    for (String field : serverChoiceIndexes) {
      if (field == null) {
        throw new NullPointerException("serverChoiceFields must not contain null elements");
      }
      if (field.trim().isEmpty()) {
        throw new IllegalArgumentException("serverChoiceFields must not contain emtpy field names");
      }
      int pos = field.indexOf('"');
      if (pos >= 0) {
        throw new IllegalArgumentException("field contains double quote at position " + pos+1 + ": " + field);
      }
      pos = field.indexOf('\'');
      if (pos >= 0) {
        throw new IllegalArgumentException("field contains single quote at position " + pos+1 + ": " + field);
      }
    }
    this.serverChoiceIndexes = serverChoiceIndexes;
  }

  public String cql2pgJson(String cql) {
    try {
      CQLParser parser = new CQLParser();
      CQLNode node = parser.parse(cql);
      return pg(node);
    } catch (IOException|CQLParseException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private String pg(CQLNode node) {
    if (node instanceof CQLTermNode) {
      return pg((CQLTermNode) node);
    }
    if (node instanceof CQLBooleanNode) {
      return pg((CQLBooleanNode) node);
    }
    if (node instanceof CQLSortNode) {
      return pg((CQLSortNode) node);
    }
    throw new IllegalArgumentException("Not implemented yet: " + node.getClass().getName());
  }

  private String pg(CQLSortNode node) {
    StringBuilder order = new StringBuilder();
    order.append(pg(node.getSubtree()))
    .append(" ORDER BY ");
    boolean firstIndex = true;
    for (ModifierSet modifierSet : node.getSortIndexes()) {
      if (firstIndex) {
        firstIndex = false;
      } else {
        order.append(", ");
      }
      String index = modifierSet.getBase();
      order.append(jsonField).append("->'").append(index.replace(".", "'->'")).append("'");

      CqlModifiers modifiers = new CqlModifiers(modifierSet);
      if (modifiers.cqlSort == CqlSort.DESCENDING) {
        order.append(" DESC");
      }  // ASC not needed, it's Postgres' default
    }
    return order.toString();
  }

  private String sqlOperator(CQLBooleanNode node) {
    if (node instanceof CQLAndNode) {
      return "AND";
    }
    if (node instanceof CQLOrNode) {
      return "OR";
    }
    if (node instanceof CQLNotNode) {
      // CQL "NOT" means SQL "AND NOT", see section "7. Boolean Operators" in
      // https://www.loc.gov/standards/sru/cql/spec.html
      return "AND NOT";
    }
    throw new IllegalArgumentException("Not implemented yet: " + node.getClass().getName());
  }

  private String pg(CQLBooleanNode node) {
    return "(" + pg(node.getLeftOperand()) + ") "
        + sqlOperator(node)
        + " (" + pg(node.getRightOperand()) + ")";
  }

  /**
   * unicode.getEquivalents(c) but with \ and " masked using backslash.
   * @param unicode quivalence to use
   * @param c  character to use
   * @return masked equivalents
   */
  private static String equivalents(Unicode unicode, char c) {
    String s = unicode.getEquivalents(c);
    // JSON requires special quoting of \ and ".
    // The blackslash needs to be doubled for Java, Postgres and JSON each (2*2*2=8)
    if (s.startsWith("[\\")) {  // s == [\﹨＼]
      return "(\\\\|[" + s.substring(2) + ")";
    }
    if (s.startsWith("[\"")) {  // s == ["＂]
      return "(\\\\\"|[" + s.substring(2) + ")";
    }

    return s;
  }

  private static String regexp(Unicode unicode, String s) {
    StringBuilder regexp = new StringBuilder();
    boolean backslash = false;
    for (char c : s.toCharArray()) {
      if (backslash) {
        // Backslash (\) is used to escape '*', '?', quote (") and '^' , as well as itself.
        // Backslash followed by any other characters is an error (see cql spec), but
        // we handle it gracefully matching that character.
        regexp.append(equivalents(unicode, c));
        backslash = false;
        continue;
      }
      switch (c) {
      case '\\':
        backslash = true;
        break;
      case '?':
        regexp.append(WORD_CHARACTER_REGEXP);
        break;
      case '*':
        regexp.append(WORD_CHARACTER_REGEXP + "*");
        break;
      case '^':
        regexp.append("(^|$)");
        break;
      default:
        regexp.append(equivalents(unicode, c));
      }
    }

    if (backslash) {
      // a single backslash at the end is an error but we handle it gracefully matching one.
      regexp.append(equivalents(unicode, '\\'));
    }

    // mask ' used for quoting postgres strings
    return regexp.toString().replace("'", "''");
  }

  private static Unicode unicode(CqlModifiers modifiers) {
    if (modifiers.cqlCase == CqlCase.IGNORE_CASE) {
      if (modifiers.cqlAccents == CqlAccents.IGNORE_ACCENTS) {
        return Unicode.IGNORE_CASE_AND_ACCENTS;
      } else {
        return Unicode.IGNORE_CASE;
      }
    } else {
      if (modifiers.cqlAccents == CqlAccents.IGNORE_ACCENTS) {
        return Unicode.IGNORE_ACCENTS;
      } else {
        return Unicode.IGNORE_NONE;
      }
    }
  }

  private static String regexp(CqlModifiers modifiers, String s) {
    return regexp(unicode(modifiers), s);
  }

  /**
   * Return a POSIX regexp expression for each cql expression in cql.
   * @param modifiers  CqlModifiers to use
   * @param cql   expression to convert
   * @return resulting regexps
   */
  private static String [] cql2regexp(CqlModifiers modifiers, String cql) {
    String [] split = cql.split("\\s+");  // split at whitespace
    if (split.length == 0) {
      // cql contains whitespace only.
      // honorWhitespace is not implemented yet,
      // whitespace only results in empty string and matches anything
      return new String [] { " ~ ''" };
    }
    Unicode unicode = unicode(modifiers);
    for (int i=0; i<split.length; i++) {
      split[i] = " ~ '(^|[[:punct:]]|[[:space:]])"
          + regexp(unicode, split[i])
          + "($|[[:punct:]]|[[:space:]])'";

    }
    return split;
  }

  private String [] match(CQLTermNode node) {
    CqlModifiers modifiers = new CqlModifiers(node);
    if (modifiers.cqlMasking != CqlMasking.MASKED) {
      throw new IllegalArgumentException("This masking is not implemented yet: " + modifiers.cqlMasking);
    }
    String comparator = node.getRelation().getBase();
    switch (comparator) {
    case "==":
      // accept quotes at beginning and end because JSON string are
      // quoted (but JSON numbers aren't)
      return new String [] {  " ~ '^" + regexp(modifiers, node.getTerm()) + "$'" };
    case "<>":
      return new String [] { " !~ '^" + regexp(modifiers, node.getTerm()) + "$'" };
    case "=":
      return cql2regexp(modifiers, node.getTerm());
    default:
      throw new IllegalArgumentException("Relation " + node.getRelation().getBase()
          + " not implemented yet: " + node.toString());
    }
  }

  /**
   * Convert index name to SQL term.
   * Example result for field=user and index=foo.bar:
   * user->'foo'->>'bar'
   *
   * @param index name to convert
   * @return SQL term
   */
  private String index2sql(String index) {
    String result = jsonField + "->'" + index.replace(".", "'->'") + "'";
    int lastArrow = result.lastIndexOf("->'");
    return result.substring(0,  lastArrow) + "->>" + result.substring(lastArrow + 2);
  }

  /**
   * Create an SQL expression where index is applied to all matches.
   * @param index   index to use
   * @param matches  list of match expressions
   * @return SQL expression
   */
  private String index2sql(String index, String [] matches) {
    StringBuilder s = new StringBuilder();
    for (String match : matches) {
      if (s.length() > 0) {
        s.append(" AND ");
      }
      s.append(index2sql(index) + match);
    }
    if (matches.length <= 1) {
      return s.toString();
    }
    return "(" + s.toString() + ")";
  }

  private String pg(CQLTermNode node) {
    String [] matches = match(node);
    if ("cql.serverChoice".equalsIgnoreCase(node.getIndex())) {
      if (serverChoiceIndexes.isEmpty()) {
        throw new IllegalStateException("cql.serverChoice requested, but not serverChoiceIndexes defined.");
      }
      return serverChoiceIndexes.stream()
          .map(index -> index2sql(index, matches))
          .collect(Collectors.joining(" OR "));
    } else {
      return index2sql(node.getIndex(), matches);
    }
  }
}
