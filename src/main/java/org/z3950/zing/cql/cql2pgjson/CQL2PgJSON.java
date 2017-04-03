package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

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
  /** Local data model of JSON schema */
  private Schema schema;

  /** JSON number, see spec at http://json.org/ */
  private static final Pattern jsonNumber = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

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
   * @throws FieldException provided field is not valid
   */
  public CQL2PgJSON(String field) throws FieldException {
    if (field == null || field.trim().isEmpty()) {
      throw new FieldException("field (containing tableName) must not be empty");
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
   * @throws FieldException (subclass of CQL2PgJSONException) provided field is not valid
   * @throws SchemaException (subclass of CQL2PgJSONException) provided JSON schema not valid
   */
  public CQL2PgJSON(String field, String schema) throws IOException, FieldException, SchemaException {
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
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   * @throws ServerChoiceIndexesException (subclass of CQL2PgJSONException) - provided serverChoiceIndexes is not valid
   */
  public CQL2PgJSON(String field, List<String> serverChoiceIndexes) throws FieldException, ServerChoiceIndexesException {
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
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   * @throws SchemaException (subclass of CQL2PgJSONException) provided JSON schema not valid
   * @throws ServerChoiceIndexesException (subclass of CQL2PgJSONException) - provided serverChoiceIndexes is not valid
   */
  public CQL2PgJSON(String field, String schema, List<String> serverChoiceIndexes)
      throws IOException, SchemaException, ServerChoiceIndexesException, FieldException {
    this(field);
    setSchema(schema);
    setServerChoiceIndexes(serverChoiceIndexes);
  }

  /**
   * Set the schema of the field.
   * @param schema  JSON String representing the schema of the field the CQL queries against.
   * @throws IOException if the JSON structure is invalid
   * @throws SchemaException if the JSON is structurally acceptable but doesn't match expected schema
   */
  private void setSchema(String schema) throws IOException, SchemaException {
    this.schema = new Schema( schema );
  }

  /**
   * Set the index names (field names) for cql.serverChoice.
   * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
   *                                  names must not contain double quote or single quote.
   * @throws ServerChoiceIndexesException if serverChoiceIndexes value(s) are invalid
   */
  public void setServerChoiceIndexes(List<String> serverChoiceIndexes) throws ServerChoiceIndexesException {
    if (serverChoiceIndexes == null) {
      this.serverChoiceIndexes = Collections.emptyList();
      return;
    }
    for (String field : serverChoiceIndexes) {
      if (field == null) {
        throw new ServerChoiceIndexesException("serverChoiceFields must not contain null elements");
      }
      if (field.trim().isEmpty()) {
        throw new ServerChoiceIndexesException("serverChoiceFields must not contain empty field names");
      }
      int pos = field.indexOf('"');
      if (pos >= 0) {
        throw new ServerChoiceIndexesException("field contains double quote at position " + pos+1 + ": " + field);
      }
      pos = field.indexOf('\'');
      if (pos >= 0) {
        throw new ServerChoiceIndexesException("field contains single quote at position " + pos+1 + ": " + field);
      }
    }
    this.serverChoiceIndexes = serverChoiceIndexes;
  }

  public String cql2pgJson(String cql) throws QueryValidationException {
    try {
      CQLParser parser = new CQLParser();
      CQLNode node = parser.parse(cql);
      return pg(node);
    } catch (IOException|CQLParseException e) {
      throw new QueryValidationException(e);
    }
  }

  private String pg(CQLNode node) throws QueryValidationException, CQLFeatureUnsupportedException {
    if (node instanceof CQLTermNode) {
      return pg((CQLTermNode) node);
    }
    if (node instanceof CQLBooleanNode) {
      return pg((CQLBooleanNode) node);
    }
    if (node instanceof CQLSortNode) {
      return pg((CQLSortNode) node);
    }
    throw new CQLFeatureUnsupportedException("Not implemented yet: " + node.getClass().getName());
  }

  private String pg(CQLSortNode node) throws QueryValidationException {
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

  private static String sqlOperator(CQLBooleanNode node) throws CQLFeatureUnsupportedException {
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
    throw new CQLFeatureUnsupportedException("Not implemented yet: " + node.getClass().getName());
  }

  private String pg(CQLBooleanNode node) throws QueryValidationException {
    String operator = sqlOperator(node);
    String isNotTrue = "";

    if ("AND NOT".equals(operator)) {
      operator = "AND (";
      isNotTrue = ") IS NOT TRUE";
      // NOT TRUE is (FALSE or NULL) to catch the NULL case when the field does not exist.
      // This completely inverts the right operand.
    }

    return "(" + pg(node.getLeftOperand()) + ") "
        + operator
        + " (" + pg(node.getRightOperand()) + isNotTrue + ")";
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
      }
      return Unicode.IGNORE_CASE;
    }
    if (modifiers.cqlAccents == CqlAccents.IGNORE_ACCENTS) {
      return Unicode.IGNORE_ACCENTS;
    }
    return Unicode.IGNORE_NONE;
  }

  private static String regexp(CqlModifiers modifiers, String s) {
    return regexp(unicode(modifiers), s);
  }

  /**
   * Return a POSIX regexp expression for each word in cql. Words are delimited by whitespace.
   * @param modifiers  CqlModifiers to use
   * @param cql   words to convert
   * @return resulting regexps
   */
  private static String [] wordRegexp(CqlModifiers modifiers, String cql) {
    String [] split = cql.trim().split("\\s+");  // split at whitespace
    if (split.length == 1 && "".equals(split[0])) {
      // The variable cql contains whitespace only. honorWhitespace is not implemented yet.
      // So there is no word at all. Therefore no restrictions for matching - anything matches.
      return new String [] { " ~ ''" };  // matches any (existing non-null) value
    }
    Unicode unicode = unicode(modifiers);
    for (int i=0; i<split.length; i++) {
      // A word is delimited by any of: the beginning ^ or the end $ of the field or
      // by punctuation or by whitespace.
      split[i] = " ~ '(^|[[:punct:]]|[[:space:]])"
          + regexp(unicode, split[i])
          + "($|[[:punct:]]|[[:space:]])'";
    }
    return split;
  }

  private String [] match(CQLTermNode node) throws CQLFeatureUnsupportedException {
    CqlModifiers modifiers = new CqlModifiers(node);
    if (modifiers.cqlMasking != CqlMasking.MASKED) {
      throw new CQLFeatureUnsupportedException("This masking is not implemented yet: " + modifiers.cqlMasking);
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
      return wordRegexp(modifiers, node.getTerm());
    case "<":
    case "<=":
    case ">":
    case ">=":
      return new String [] { comparator + "'" + node.getTerm().replace("'", "''") + "'" };
    default:
      throw new CQLFeatureUnsupportedException("Relation " + node.getRelation().getBase()
          + " not implemented yet: " + node.toString());
    }
  }

  /**
   * Test if s is a JSON number.
   * @param s  String to test
   * @return true if s is a JSON number, false otherwise
   */
  private static boolean isJsonNumber(String s) {
    return jsonNumber.matcher(s).matches();
  }

  /**
   * Returns a numeric match like ">=17" if the node term is a JSON number, null otherwise.
   * @param node  the node to get the comparator operator and the term from
   * @return  the comparison or null
   * @throws CQLFeatureUnsupportedException if cql query attempts to use unsupported operators.
   */
  static String getNumberMatch(CQLTermNode node) throws CQLFeatureUnsupportedException {
    if (! isJsonNumber(node.getTerm())) {
      return null;
    }
    String comparator = node.getRelation().getBase();
    switch (comparator) {
    case "==":
      comparator = "=";
      break;
    case "<>":
    case "=":
    case "<":
    case "<=":
    case ">":
    case ">=":
      break;
    default:
      throw new CQLFeatureUnsupportedException("Relation " + node.getRelation().getBase()
          + " not implemented yet: " + node.toString());
    }
    return comparator + node.getTerm();
  }

  /**
   * Convert index name to SQL term of type text.
   * Example result for field=user and index=foo.bar:
   * user->'foo'->>'bar'
   *
   * @param index name to convert
   * @return SQL term
   */
  private String index2sqlText(String index) {
    String result = jsonField + "->'" + index.replace(".", "'->'") + "'";
    int lastArrow = result.lastIndexOf("->'");
    return result.substring(0,  lastArrow) + "->>" + result.substring(lastArrow + 2);
  }

  /**
   * Convert index name to SQL term of type json.
   * Example result for field=user and index=foo.bar:
   * user->'foo'->'bar'
   *
   * @param index name to convert
   * @return SQL term
   */
  private String index2sqlJson(String index) {
    return jsonField + "->'" + index.replace(".", "'->'") + "'";
  }

  /**
   * Create an SQL expression where index is applied to all matches.
   * @param index  index to use
   * @param matches  list of match expressions
   * @param numberMatch  match expression for numeric comparison (null for no numeric comparison)
   * @return SQL expression
   * @throws QueryValidationException
   */
  private String index2sql(String index, String [] matches, String numberMatch) throws QueryValidationException {
    StringBuilder s = new StringBuilder();
    for (String match : matches) {
      if (s.length() > 0) {
        s.append(" AND ");
      }
      if (schema != null)
        index = schema.mapFieldNameAgainstSchema(index);
      if (numberMatch == null) {
        s.append(index2sqlText(index)).append(match);
      } else {
        /* CASE jsonb_typeof(jsonb->amount)
         * WHEN 'number' then (jsonb->>amount)::numeric = 100
         * ELSE jsonb->>amount ~ '(^|[[:punct:]]|[[:space:]])100($|[[:punct:]]|[[:space:]])'
         * END
         */
        s.append(" CASE jsonb_typeof(").append(index2sqlJson(index)).append(")")
         .append(" WHEN 'number' then (").append(index2sqlText(index)).append(")::numeric ").append(numberMatch)
         .append(" ELSE ").append(index2sqlText(index)).append(match)
         .append(" END");
      }
    }
    if (matches.length <= 1) {
      return s.toString();
    }
    return "(" + s.toString() + ")";
  }

  private String pg(CQLTermNode node) throws QueryValidationException {
    String [] matches = match(node);
    String numberMatch = getNumberMatch(node);
    if ("cql.allRecords".equalsIgnoreCase(node.getIndex())) {
      return "true";
    }
    if ("cql.serverChoice".equalsIgnoreCase(node.getIndex())) {
      if (serverChoiceIndexes.isEmpty()) {
        throw new QueryValidationException("cql.serverChoice requested, but no serverChoiceIndexes defined.");
      }
      List<String> sqlPieces = new ArrayList<>();
      for(String index : serverChoiceIndexes)
        sqlPieces.add(index2sql(index, matches, numberMatch));
      return String.join(" OR ", sqlPieces);
    }
    return index2sql(node.getIndex(), matches, numberMatch);
  }
}
