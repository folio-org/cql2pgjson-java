package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;


import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

/**
 * Convert a CQL query into a PostgreSQL JSONB SQL query.
 * <p>
 * Contextual Query Language (CQL) Specification:
 * <a href="https://www.loc.gov/standards/sru/cql/spec.html">https://www.loc.gov/standards/sru/cql/spec.html</a>
 * <p>
 * JSONB in PostgreSQL:
 * <a href="https://www.postgresql.org/docs/current/static/datatype-json.html">https://www.postgresql.org/docs/current/static/datatype-json.html</a>
 */
public class CQL2PgJSON {

  /**
   * Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   * Must conform to SQL identifier requirements (characters, not a keyword), or properly
   * quoted using double quotes.
   */
  private String jsonField = null;
  private List<String> jsonFields = null;
  /** Local data model of JSON schema */
  private Schema schema;
  private Map<String, Schema> schemas;
  private static JSONObject dbSchema; // The whole schema.json, with all tables etc
  private JSONObject dbTable; // Our primary table inside the dbSchema
  private static Logger logger = Logger.getLogger(CQL2PgJSON.class.getName());

  /** Postgres regexp that matches at any punctuation and space character
   * and at the beginning of the string */
  private static final String REGEXP_WORD_BEGIN = "(^|[[:punct:]]|[[:space:]]|(?=[[:punct:]]|[[:space:]]))";
  /** Postgres regexp that matches at any punctuation and space character
   * and at the end of the string */
  private static final String REGEXP_WORD_END = "($|[[:punct:]]|[[:space:]]|(?<=[[:punct:]]|[[:space:]]))";

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

  private static class IndexTextAndJsonValues {
    String indexText;
    String indexJson;
    /** the RAML type like integer, number, string, boolean, datetime, ... "" for unknown. */
    String type = "";
  }

  private static class CqlModifiers {
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

  private void loadDbSchema() {
    dbTable = null;
    try {
      if (dbSchema == null) {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream resourceAsStream = classLoader.getResourceAsStream("templates/db_scripts/schema.json");
        if (resourceAsStream == null) {
          logger.log(Level.SEVERE, "loadDbSchema failed to load resource 'templates/db_scripts/schema.json'");
          return;
        }
        String dbJson;
        dbJson = IOUtils.toString(resourceAsStream, "UTF-8");
        dbSchema = new JSONObject(dbJson);
        logger.log(Level.INFO, "loadDbSchema: Loaded 'templates/db_scripts/schema.json' OK");
      }
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "No schema.json found", ex);
    }
    if (dbSchema.has("tables")) {
      if (jsonField == null) {
        logger.log(Level.SEVERE, "loadDbSchema(): No primary table name, can not load");
        return;
      }
      // Remove the json blob field name, usually ".jsonb", but in tests also
      // ".user_data" etc.
      String tname = this.jsonField.replaceAll("\\.[^.]+$", "");
      this.dbTable = findItem(dbSchema.getJSONArray("tables"), "tableName", tname);
      if (this.dbTable == null) {
        logger.log(Level.SEVERE, "loadDbSchema loadDbSchema(): Table {0} NOT FOUND", tname);
      }
    } else {
      logger.log(Level.SEVERE, "loadDbSchema loadDbSchema(): No 'tables' section found");
    }
  }

  /**
   * Create an instance for the specified schema.
   *
   * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
   *   quoted using double quotes.
   * @throws FieldException provided field is not valid
   */
  public CQL2PgJSON(String field) throws FieldException {
    this.jsonField = trimNotEmpty(field);
    loadDbSchema();
  }

  /**
   * Create an instance for the specified schema.
   *
   * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
   *   quoted using double quotes.
   * @param schemaJson JSON String representing the schema of the field the CQL queries against.
   * @throws IOException if the JSON structure is invalid
   * @throws FieldException (subclass of CQL2PgJSONException) provided field is not valid
   * @throws SchemaException (subclass of CQL2PgJSONException) provided JSON schema not valid
   */
  public CQL2PgJSON(String field, String schemaJson) throws IOException, FieldException, SchemaException {
    this(field);
    setSchema(schemaJson);
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
   * @param schemaJson JSON String representing the schema of the field the CQL queries against.
   * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
   *                                  names must not contain double quote or single quote.
   * @throws IOException if the JSON structure is invalid
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   * @throws SchemaException (subclass of CQL2PgJSONException) provided JSON schema not valid
   * @throws ServerChoiceIndexesException (subclass of CQL2PgJSONException) - provided serverChoiceIndexes is not valid
   */
  public CQL2PgJSON(String field, String schemaJson, List<String> serverChoiceIndexes)
      throws IOException, SchemaException, ServerChoiceIndexesException, FieldException {
    this(field);
    setSchema(schemaJson);
    setServerChoiceIndexes(serverChoiceIndexes);
  }

  /**
   * Create an instance for the specified list of schemas. If only one field name is provided, queries will
   * default to the handling of single field queries.
   *
   * @param fields Field names of the JSON fields, may include schema and table name (e.g. tenant1.user_table.json).
   *  Must conform to SQL identifier requirements (characters, not a keyword), or properly quoted using double quotes.
   *  The first field name on the list will be the default field for terms in queries that don't specify a json field.
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   */
  public CQL2PgJSON(List<String> fields) throws FieldException {
    if (fields == null || fields.isEmpty())
      throw new FieldException( "fields list must not be empty" );
    this.jsonFields = new ArrayList<>();
    for (String field : fields) {
      this.jsonFields.add(trimNotEmpty(field));
    }
    if (this.jsonFields.size() == 1)
      this.jsonField = this.jsonFields.get(0);
    this.schemas = new HashMap<>();
    loadDbSchema();
  }

  /**
   * Create an instance for the specified list of schemas. If only one field name is provided, queries will
   * default to the handling of single field queries.
   *
   * @param fields Field names of the JSON fields, may include schema and table name (e.g. tenant1.user_table.json).
   *  Must conform to SQL identifier requirements (characters, not a keyword), or properly quoted using double quotes.
   *  The first field name on the list will be the default field for terms in queries that don't specify a json field.
   * @param serverChoiceIndexes  List of field names, may be empty, must not contain null,
   *                             names must not contain double quote or single quote and must identify the jsonb
   *                             field to which they apply. (e.g. "group_jsonb.patronGroup.group" )
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   * @throws ServerChoiceIndexesException (subclass of CQL2PgJSONException) - provided serverChoiceIndexes is not valid
   */
  public CQL2PgJSON(List<String> fields, List<String> serverChoiceIndexes)
      throws ServerChoiceIndexesException, FieldException {
    this(fields);
    setServerChoiceIndexes(serverChoiceIndexes);
  }

  /**
   * Create an instance for the specified list of schemas. If only one field name is provided, queries will
   * default to the handling of single field queries.
   *
   * @param fieldsAndSchemaJsons Field names of the JSON fields as keys,
   *  JSON String representing the schema of the field the CQL queries against as values.
   *  Field names may include schema and table name, (e.g. tenant1.user_table.json) and must conform to
   *  SQL identifier requirements (characters, not a keyword), or properly quoted using double quotes.
   *  Schemas values may be null if a particular field has no available schema.
   *  The first field name in the map will be the default field for terms in queries that don't specify a json field.
   * @throws IOException if the JSON structure is invalid
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   * @throws SchemaException (subclass of CQL2PgJSONException) provided JSON schema not valid
   */
  public CQL2PgJSON(Map<String,String> fieldsAndSchemaJsons)
      throws FieldException, IOException, SchemaException {
    if (fieldsAndSchemaJsons == null || fieldsAndSchemaJsons.isEmpty()) {
      throw new FieldException( "fields map must not be empty" );
    }
    this.jsonFields = new ArrayList<>();
    this.schemas = new HashMap<>();
    for (Entry<String,String> e : fieldsAndSchemaJsons.entrySet()) {
      String field = trimNotEmpty(e.getKey());
      this.jsonFields.add(field);
      String schemaJson = StringUtils.defaultString(e.getValue());
      if (schemaJson.isEmpty()) {
        continue;
      }
      this.schemas.put(field, new Schema(schemaJson));
    }
    if (this.jsonFields.size() == 1) {
      this.jsonField = this.jsonFields.get(0);
      if (this.schemas.containsKey(this.jsonField)) {
        this.schema = this.schemas.get(this.jsonField);
      }
    }
  }

  /**
   * Create an instance for the specified list of schemas.
   *
   * @param fieldsAndSchemaJsons Field names of the JSON fields as keys,
   *   JSON String representing the schema of the field the CQL queries against as values.
   *   Field names may include schema and table name, (e.g. tenant1.user_table.json) and must conform to
   *   SQL identifier requirements (characters, not a keyword), or properly quoted using double quotes.
   *   Schemas values may be null if a particular field has no available schema.
   *  The first field name on the list will be the default field for terms in queries that don't specify a json field.
   * @param serverChoiceIndexes  List of field names, may be empty, must not contain null,
   *                             names must not contain double quote or single quote and must either identify the
   *                             jsonb field to which they apply (e.g. "group_jsonb.patronGroup.group" ) or if
   *                             all included fields have schemas provided they must be entirely unambiguous.
   * @throws IOException if the JSON structure is invalid
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   * @throws SchemaException (subclass of CQL2PgJSONException) provided JSON schema not valid
   * @throws ServerChoiceIndexesException (subclass of CQL2PgJSONException) - provided serverChoiceIndexes is not valid
   */
  public CQL2PgJSON(Map<String,String> fieldsAndSchemaJsons, List<String> serverChoiceIndexes)
      throws FieldException, IOException, SchemaException, ServerChoiceIndexesException  {
    this(fieldsAndSchemaJsons);
    setServerChoiceIndexes(serverChoiceIndexes);
  }

  public String getjsonField() {
    return this.jsonField;
  }
  /**
   * Scans through a JsonArray, looking for a record that has a given value in a
   * given field. For example "tableName" that matches "users". If found,
   * returns the whole item.
   */
  private JSONObject findItem(JSONArray arr, String key, String value) {
    if (arr == null) {
      return null;
    }
    Iterator<Object> it = arr.iterator();
    while (it.hasNext()) {
      Object e = it.next();
      if (e instanceof JSONObject) {
        JSONObject item = (JSONObject) e;
        String name = item.getString(key);
        if (value.equalsIgnoreCase(name)) {
          return item;
        }
      } // else ???
    }
    return null;
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

  /**
   * Return field.trim(). Throw FieldException if field is null or
   * field.trim() is empty.
   *
   * @param field  the field name to trim
   * @return trimmed field
   * @throws FieldException  if field is null or the trimmed field name is empty
   */
  private String trimNotEmpty(String field) throws FieldException {
    if (field == null) {
      throw new FieldException("a field name must not be null");
    }
    String fieldTrimmed = field.trim();
    if (fieldTrimmed.isEmpty()) {
      throw new FieldException("a field name must not be empty");
    }
    return fieldTrimmed;
  }

  /**
   * Return an SQL WHERE clause for the CQL expression.
   * @param cql  CQL expression to convert
   * @return SQL WHERE clause, without leading "WHERE ", may contain "ORDER BY" clause
   * @throws QueryValidationException  when parsing or validating cql fails
   */
  public String cql2pgJson(String cql) throws QueryValidationException {
    try {
      CQLParser parser = new CQLParser();
      CQLNode node = parser.parse(cql);
      return pg(node);
    } catch (IOException|CQLParseException e) {
      throw new QueryValidationException(e);
    }
  }

  /**
   * Convert the CQL query into a SQL query and return the WHERE and the ORDER BY clause.
   * @param cql  the query to convert
   * @return SQL query
   * @throws QueryValidationException
   */
  public SqlSelect toSql(String cql) throws QueryValidationException {
    try {
      CQLParser parser = new CQLParser();
      CQLNode node = parser.parse(cql);
      return toSql(node);
    } catch (IOException|CQLParseException e) {
      throw new QueryValidationException(e);
    }
  }

  private SqlSelect toSql(CQLNode node) throws QueryValidationException {
    if (node instanceof CQLSortNode) {
      return toSql((CQLSortNode) node);
    }
    return new SqlSelect(pg(node), null);
  }

  private String pg(CQLNode node) throws QueryValidationException {
    if (node instanceof CQLTermNode) {
      return pg((CQLTermNode) node);
    }
    if (node instanceof CQLBooleanNode) {
      return pg((CQLBooleanNode) node);
    }
    if (node instanceof CQLSortNode) {
      SqlSelect sqlSelect = toSql((CQLSortNode) node);
      return sqlSelect.getWhere() + " ORDER BY " + sqlSelect.getOrderBy();
    }
    throw createUnsupportedException(node);
  }

  private static CQLFeatureUnsupportedException createUnsupportedException(CQLNode node) {
    return new CQLFeatureUnsupportedException("Not implemented yet: " + node.getClass().getName());
  }

  /**
   * Return "lower(f_unaccent(" + term + "))".
   * @param term  String to wrap
   * @return wrapped term
   */
  private static String wrapInLowerUnaccent(String term) {
    return "lower(f_unaccent(" + term + "))";
  }

  /**
   * Return $term, lower($term), f_unaccent($term) or lower(f_unaccent($term))
   * according to the cqlModifiers.  If undefined use CqlAccents.IGNORE_ACCENTS
   * and CqlCase.IGNORE_CASE as default.
   * @param term  the String to wrap
   * @param cqlModifiers  what functions to use
   * @return wrapped term
   */
  private static String wrapInLowerUnaccent(String term, CqlModifiers cqlModifiers) {
    String result = term;
    if (cqlModifiers.cqlAccents != CqlAccents.RESPECT_ACCENTS) {
      result = "f_unaccent(" + result + ")";
    }
    if (cqlModifiers.cqlCase != CqlCase.RESPECT_CASE) {
      result = "lower(" + result + ")";
    }
    return result;
  }

  private SqlSelect toSql(CQLSortNode node) throws QueryValidationException {
    StringBuilder order = new StringBuilder();
    String where = pg(node.getSubtree());

    boolean firstIndex = true;
    for (ModifierSet modifierSet : node.getSortIndexes()) {
      if (firstIndex) {
        firstIndex = false;
      } else {
        order.append(", ");
      }

      String desc = "";
      CqlModifiers modifiers = new CqlModifiers(modifierSet);
      if (modifiers.cqlSort == CqlSort.DESCENDING) {
        desc = " DESC";
      }  // ASC not needed, it's Postgres' default

      IndexTextAndJsonValues vals = getIndexTextAndJsonValues(modifierSet.getBase());

      switch (vals.type) {
      case "number":
      case "integer":
        order.append(vals.indexJson).append(desc);
        continue;
      default:
        break;
      }

      // We assume that a CREATE INDEX for this has been installed.
      order.append(wrapInLowerUnaccent(vals.indexText)).append(desc);
    }
    return new SqlSelect(where, order.toString());
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
    throw createUnsupportedException(node);
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
   * The LIKE expressions for matching a string. The caller needs to AND them.
   * <p>
   * Example 1: IGNORE_ACCENTS, IGNORE_CASE, trueOnMatch=true, s="Sövan*"<br>
   * { "lower(f_unaccent(textIndex)) LIKE lower(f_unaccent('Sövan%'))" }
   * <p>
   * Example 2: IGNORE_ACCENTS, IGNORE_CASE, trueOnMatch=false, s="Sövan*"<br>
   * { "lower(f_unaccent(textIndex)) NOT LIKE lower(f_unaccent('Sövan%'))" }
   * <p>
   * Example 3: RESPECT_ACCENTS, RESPECT_CASE, trueOnMatch=true, s="Sövan*"<br>
   * { "lower(f_unaccent(textIndex)) LIKE lower(f_unaccent('Sövan%'))",
   *   "textIndex LIKE 'Sövan%'" }<br>
   * The first LIKE uses the index, the second ensures accents and case.
   *
   * @param textIndex  JSONB field to match against
   * @param modifiers CqlModifiers to use
   * @param s string to match
   * @param trueOnMatch boolean result in case of match. true for LIKE and false for NOT LIKE.
   * @return the sql match expression
   */
  @SuppressWarnings("squid:S1192")  // suppress "String literals should not be duplicated"
  private static String [] fullMatch(String textIndex, CqlModifiers modifiers, String s, boolean trueOnMatch) {
    String likeOperator = trueOnMatch ? " LIKE " : " NOT LIKE ";
    String like = "'" + Cql2SqlUtil.cql2like(s) + "'";
    String indexMatch = wrapInLowerUnaccent(textIndex)
        + likeOperator + wrapInLowerUnaccent(like);
    if (modifiers.cqlAccents == CqlAccents.IGNORE_ACCENTS &&
        modifiers.cqlCase    == CqlCase.IGNORE_CASE         ) {
      return new String [] { indexMatch };
    }
    return new String [] { indexMatch,
        wrapInLowerUnaccent(textIndex, modifiers) + likeOperator + wrapInLowerUnaccent(like, modifiers) };
  }

  /**
   * Return SQL regexp expressions for do an "all" CQL match of the cql string.
   * "all" means that all words match, in any position.
   * <p>
   * It matches if all returned expressions are true, the caller needs to "AND" them.
   * Words are delimited by whitespace, punctuation, or start or end of field.
   *
   * @param textIndex  JSONB field to match against
   * @param modifiers  CqlModifiers to use
   * @param cql   words to convert
   * @return resulting regexps
   */
  @SuppressWarnings("squid:S1192")  // suppress "String literals should not be duplicated"
  private static String [] allRegexp(String textIndex, CqlModifiers modifiers, String cql) {
    String [] split = cql.trim().split("\\s+");  // split at whitespace
    if (split.length == 1 && "".equals(split[0])) {
      // The variable cql contains whitespace only. honorWhitespace is not implemented yet.
      // So there is no word at all. Therefore no restrictions for matching - anything matches.
      return new String [] { textIndex + " ~ ''" };  // matches any (existing non-null) value
      // don't use "TRUE" because that also matches when the field is not defined or is null
    }
    for (int i=0; i<split.length; i++) {
      // A word is delimited by any of: the beginning ^ or the end $ of the field or
      // by punctuation or by whitespace.
      String regexp = "'" + REGEXP_WORD_BEGIN
          + Cql2SqlUtil.cql2regexp(split[i]) + REGEXP_WORD_END + "'";
      split[i] = wrapInLowerUnaccent(textIndex) + " ~ " + wrapInLowerUnaccent(regexp);
      if (modifiers.cqlAccents == CqlAccents.RESPECT_ACCENTS ||
          modifiers.cqlCase == CqlCase.RESPECT_CASE) {
        split[i] = "(" + split[i] + " AND " +
            wrapInLowerUnaccent(textIndex, modifiers) + " ~ " +
            wrapInLowerUnaccent(regexp,    modifiers) + ")";
      }
    }
    return split;
  }

  /**
   * Return SQL regexp expressions for do an "adj" CQL match (phrase match) of the cql string.
   * "adj" means that all words match, and must be adjacent to each other
   * in the order they are in the cql string. Other words may be before or after
   * the phrase.
   * <p>
   * It matches if all returned expressions are true, the caller needs to "AND" them.
   * Words are delimited by whitespace, punctuation, or start or end of field.
   *
   * @param textIndex  JSONB field to match against
   * @param modifiers  CqlModifiers to use
   * @param cql   words to convert
   * @return resulting regexps
   */
  private static String [] adjRegexp(String textIndex, CqlModifiers modifiers, String cql) {
    String [] split = cql.trim().split("\\s+");  // split at whitespace
    if (split.length == 1 && "".equals(split[0])) {
      // The variable cql contains whitespace only. honorWhitespace is not implemented yet.
      // So there is no word at all. Therefore no restrictions for matching - anything matches.
      return new String [] { textIndex + " ~ ''" };  // matches any (existing non-null) value
      // don't use "TRUE" because that also matches when the field is not defined or is null
    }
    StringBuilder regexp = new StringBuilder();
    regexp.append("'").append(REGEXP_WORD_BEGIN);
    for (int i=0; i<split.length; i++) {
      if (i > 0) {
        regexp.append("([[:punct:]]|[[:space:]])+");
      }
      regexp.append(Cql2SqlUtil.cql2regexp(split[i]));
    }
    regexp.append(REGEXP_WORD_END).append("'");

    String regexpString = regexp.toString();

    String result = wrapInLowerUnaccent(textIndex) + " ~ " + wrapInLowerUnaccent(regexpString);
    if (modifiers.cqlAccents == CqlAccents.RESPECT_ACCENTS ||
        modifiers.cqlCase == CqlCase.RESPECT_CASE) {
      result = "(" + result + " AND " +
          wrapInLowerUnaccent(textIndex,    modifiers) + " ~ " +
          wrapInLowerUnaccent(regexpString, modifiers) + ")";
    }
    return new String [] { result };
  }

  private static String [] match(String textIndex, CQLTermNode node) throws CQLFeatureUnsupportedException {
    CqlModifiers modifiers = new CqlModifiers(node);
    if (modifiers.cqlMasking != CqlMasking.MASKED) {
      throw new CQLFeatureUnsupportedException("This masking is not implemented yet: " + modifiers.cqlMasking);
    }
    String comparator = node.getRelation().getBase();
    switch (comparator) {
    case "==":
      return fullMatch(textIndex, modifiers, node.getTerm(), true);
    case "<>":
      return fullMatch(textIndex, modifiers, node.getTerm(), false);
    case "all":
      return allRegexp(textIndex, modifiers, node.getTerm());
    case "=":   // use "adj"
    case "adj":
      return adjRegexp(textIndex, modifiers, node.getTerm());
    case "any":
      String [] matches = allRegexp(textIndex, modifiers, node.getTerm());
      if (matches.length == 1) {
        return matches;
      }
      return new String [] { "(" + String.join(") OR (", matches) + ")" };
    case "<":
    case "<=":
    case ">":
    case ">=":
      return new String [] { textIndex + " " + comparator + "'" + node.getTerm().replace("'", "''") + "'" };
    default:
      throw new CQLFeatureUnsupportedException("Relation " + node.getRelation().getBase()
          + " not implemented yet: " + node.toString());
    }
  }

  /**
   * Returns a numeric match like >='"17"' if the node term is a JSON number, null otherwise.
   * @param node  the node to get the comparator operator and the term from
   * @return  the comparison or null
   * @throws CQLFeatureUnsupportedException if cql query attempts to use unsupported operators.
   */
  static String getNumberMatch(CQLTermNode node) {
    if (! Cql2SqlUtil.isPostgresNumber(node.getTerm())) {
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
      return null;
    }
    return comparator + "'\"" +  node.getTerm() + "\"'";
  }

  /**
   * Convert index name to SQL term of type text.
   * Example result for field=user and index=foo.bar:
   * user->'foo'->>'bar'
   * @param jsonField
   * @param index name to convert
   *
   * @return SQL term
   */
  private static String index2sqlText(String jsonField, String index) {
    String result = jsonField + "->'" + index.replace(".", "'->'") + "'";
    int lastArrow = result.lastIndexOf("->'");
    return result.substring(0,  lastArrow) + "->>" + result.substring(lastArrow + 2);
  }

  /**
   * Convert index name to SQL term of type json.
   * Example result for field=user and index=foo.bar:
   * user->'foo'->'bar'
   * @param jsonField
   * @param index name to convert
   *
   * @return SQL term
   */
  private static String index2sqlJson(String jsonField, String index) {
    return jsonField + "->'" + index.replace(".", "'->'") + "'";
  }

  /**
   * Append all strings to the stringBuilder.
   * <p>
   * append(sb, "abc", "123") is more easy to read than
   * sb.append("abc").append("123).
   * @param stringBuilder where to append
   * @param strings what to append
   */
  private static void append(StringBuilder stringBuilder, String ... strings) {
    for (String string : strings) {
      stringBuilder.append(string);
    }
  }

  private IndexTextAndJsonValues getIndexTextAndJsonValues(String index)
      throws QueryValidationException {
    if (jsonField == null) {
      return multiFieldProcessing(index);
    }
    IndexTextAndJsonValues vals = new IndexTextAndJsonValues();
    String finalIndex = index;
    if (schema != null) {
      Schema.Field field = schema.mapFieldNameAgainstSchema(index);
      finalIndex = field.getPath();
      vals.type = field.getType();
    }
    vals.indexJson = index2sqlJson(this.jsonField, finalIndex);
    vals.indexText = index2sqlText(this.jsonField, finalIndex);
    return vals;
  }

  /**
   * Replace '" by to_jsonb(
   * and '" by )
   * <p>
   * numberMatch("'\"100\"'") = "to_jsonb(100)"
   * <p>
   * to_jsonb(01) is valid Postgres syntax and results in valid JSON '"1"'.
   * '"01"' fails because 01 is an invalid JSON number.
   * @param match  where to search
   * @return the replaced string
   */
  private static String numberMatch(String jsonMatch) {
    return jsonMatch.replace("'\"", "to_jsonb(").replace("\"'", ")");
  }

  /**
   * True if node's relation is a numeric comparison. "=" isn't because
   * it may be a string comparison.
   * @param node
   * @return true iff numeric comparison
   */
  private static boolean isNumberComparator(CQLTermNode node) {
    switch (node.getRelation().getBase()) {
    case "<>":
    case "<":
    case "<=":
    case ">":
    case ">=":
    case "==":
      return true;
    default:
      // this includes "=" which may be a string match
      return false;
    }
  }

  /**
   * Create an SQL expression where index is applied to all matches.
   * @param index  index to use
   * @param matches  list of match expressions
   * @param jsonMatch  match expression for numeric comparison (null for no numeric comparison),
   *                   with single quotes (Postgres) and double quotes (JSON), for example >='"34"'
   * @return SQL expression
   * @throws QueryValidationException
   */
  @SuppressWarnings("squid:S1192")  // suppress "String literals should not be duplicated"
  private String index2sql(String index, CQLTermNode node, String jsonMatch) throws QueryValidationException {

    if (dbTable != null) {
      JSONObject ftIndex = null;
      if (dbTable.has("fullTextIndex")) {
        JSONArray ftIndexes = dbTable.getJSONArray("fullTextIndex");
        ftIndex = findItem(ftIndexes, "fieldName", index);
        if (ftIndex != null) {
          return pgFT(node, ftIndex, index);
        }
      }
    }

    IndexTextAndJsonValues vals = getIndexTextAndJsonValues(index);

    if (vals.type.equals("") && jsonMatch != null && isNumberComparator(node)) {
      // numberMatch: Both sides of the comparison operator are JSONB expressions.

      // When comparing two JSONBs a JSONB containing any string is bigger than
      // any JSONB containing any number.
      // Therefore we need to check the jsonb_typeof, which is supported by a
      // ((jsonb->'amount')) index.

      /* (   ( jsonb_typeof(jsonb->'amount')= 'number' AND jsonb->'amount' < to_jsonb(100)  )
       *  OR ( jsonb_typeof(jsonb->'amount')<>'number' AND jsonb->'amount' <        '"100"' )
       * )
       */
      StringBuilder s = new StringBuilder();
      append(s,
          "((",
          "jsonb_typeof(", vals.indexJson, ")='number'",
          " AND ", vals.indexJson, numberMatch(jsonMatch),
          ") OR (",
          "jsonb_typeof(", vals.indexJson, ")<>'number'",
          " AND ", vals.indexJson, jsonMatch);
      if (jsonMatch.startsWith("=")) {
        append(s, " AND ", wrapInLowerUnaccent(vals.indexText), jsonMatch.replace("\"", ""));
      }
      append(s, "))");
      return s.toString();
    }

    if (jsonMatch != null &&
        ("integer".equals(vals.type) || "number".equals(vals.type))) {
        return vals.indexJson + numberMatch(jsonMatch);
    }

    String [] matches = match(vals.indexText, node);
    String s = String.join(" AND ", matches);
    if (matches.length <= 1) {
      return s;
    } else {
      return "(" + s + ")";
    }
  }

  private IndexTextAndJsonValues multiFieldProcessing( String index ) throws QueryValidationException {
    IndexTextAndJsonValues vals = new IndexTextAndJsonValues();

    // processing for case where index is prefixed with json field name
    for (String f : jsonFields) {
      if (index.startsWith(f+'.')) {
        String indexTermWithinField;
        if (schemas.containsKey(f)) {
          Schema.Field field = schemas.get(f).mapFieldNameAgainstSchema( index.substring(f.length()+1) );
          indexTermWithinField = field.getPath();
          vals.type = field.getType();
        } else {
          indexTermWithinField = index.substring(f.length()+1);
        }
        vals.indexJson = index2sqlJson(f, indexTermWithinField);
        vals.indexText = index2sqlText(f, indexTermWithinField);
        return vals;
      }
    }

    // if no json field name prefix is found, the default field name gets applied.
    String defaultJsonField = this.jsonFields.get(0);
    String finalIndex = index;
    if (schemas.containsKey(defaultJsonField)) {
      Schema.Field field = schemas.get(defaultJsonField).mapFieldNameAgainstSchema(index);
      finalIndex = field.getPath();
      vals.type = field.getType();
    }
    vals.indexJson = index2sqlJson(defaultJsonField, finalIndex);
    vals.indexText = index2sqlText(defaultJsonField, finalIndex);
    return vals;
  }

  private String pg(CQLTermNode node) throws QueryValidationException {
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
        sqlPieces.add(index2sql(index, node, numberMatch));
      return String.join(" OR ", sqlPieces);
    }
    return index2sql(node.getIndex(), node, numberMatch);
  }

  /**
   * Normalize a term for FT searching. Escape quotes, masking, etc
   *
   * @param term
   * @return
   */
  private String FTTerm(String term) throws QueryValidationException {
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < term.length(); i++) {
      // CQL specials
      char c = term.charAt(i);
      switch (c) {
        case '?':
          throw new QueryValidationException("CQL: single character mask unsupported (?)");
        case '*':
          if (i == term.length() - 1) {
            res.append(":*");
            continue;
          } else {
            throw new QueryValidationException("CQL: only right truncation supported");
          }
        case '\\':
          if (i == term.length() - 1) {
            continue;
          }
          i++;
          c = term.charAt(i);
          break;
        case '^':
          throw new QueryValidationException("CQL: anchoring unsupported (^)");
      }
      if (c == '\'') {
        if (res.length() > 0) {
          res.append("''"); // double up single quotes
        } // but not in the beginning of the term, won't work.
        continue;
      }
      // escape for FT
      if ("&!|()<>*:\\".indexOf(c) != -1) {
        res.append("\\");
      }
      res.append(c);
    }
    return res.toString();
  }

  /**
   * Translates a term node.
   *
   * @param node
   * @return
   * @throws QueryValidationException
   */
  private String pgFT(CQLTermNode node, JSONObject ftIndex, String index) throws QueryValidationException {
    String comparator = node.getRelation().getBase();
    if (!node.getRelation().getModifiers().isEmpty()) {
      throw new QueryValidationException("CQL: Unsupported modifier "
        + node.getRelation().getModifiers().get(0).getType());
    }
    final String fld = index2sqlText(this.jsonField, index);
    if ((comparator.equals("=") || comparator.equals("<>")
      || comparator.equals("adj") || comparator.equals("any") || comparator.equals("all"))
      && ftIndex != null) { // fulltext search
      if (node.getTerm().isEmpty() && (comparator.equals("=") || comparator.equals("adj")
        || comparator.equals("any") || comparator.equals("all"))) {
        // field = "" means that the field is defined, for any value, even empty
        String sql = fld + " ~ ''";
        logger.log(Level.FINE, "pgFT(): special case: empty term ''='' {0}", sql);
        return sql;
      }
      String[] words = node.getTerm().trim().split("\\s+");  // split at whitespace
      for (int i = 0; i < words.length; i++) {
        words[i] = FTTerm(words[i]);
      }
      String tsTerm = "";
      switch (comparator) {
        case "=":
        case "adj":
          tsTerm = String.join("<->", words);
          break;
        case "any":
          tsTerm = String.join(" | ", words);
          break;
        case "all":
          tsTerm = String.join(" & ", words);
          break;
        case "<>":
          tsTerm = "!(" + String.join("<->", words) + ")";
          break;
      }
      logger.log(Level.FINE, "pgFT(): term={0} ts={1}",
        new Object[]{node.getTerm(), tsTerm});
      return "to_tsvector('english', " + fld + ") "
        + "@@ to_tsquery('english','" + tsTerm + "')";
    } else {
      return pgFtNonTs(index, node, comparator, fld);
    }
  }

  private String pgFtNonTs(String index, CQLTermNode node, String comparator, final String fld) throws QueryValidationException {
    // not fulltext, regular search
    boolean found = false;
    for (String idxType : Arrays.asList("index", "uniqueIndex", "fullTextIndex")) {
      if (dbTable.has(idxType)) {
        JSONArray indexArr = dbTable.getJSONArray(idxType);
        if (findItem(indexArr, "fieldName", index) != null) {
          found = true;
          logger.log(Level.FINE, "pgFT(): Found {0} {1}",
            new Object[]{idxType, index});
        }
      }
    }
    if (!found) {
      String sub = subQuery1FT(node, index);
      if (sub != null) {
        return sub;
      }
      sub = subQuery2FT(node, index);
      if (sub != null) {
        return sub;
      }
      throw new QueryValidationException("CQL: No index '" + index + "'");
    }
    if ((comparator.equals("=") && node.getTerm().isEmpty())) {
      // field = "" means that the field is defined, for any value, even empty
      // We should check if we have an index for the field, or a foreignKey
      // Is this the right place to handle this? For now, we only get here with
      // fulltext indexes, so this is ok.
      String sql = fld + " ~ ''";
      logger.log(Level.FINE, "pgFT(): special case: empty term ''='' {0}", sql);
      return sql;
    }
    switch (comparator) {
      case "==":
        comparator = "=";
        break;
      case "=": // exact match
      case "<>":
      case "<":
      case "<=":
      case ">":
      case ">=":
        break;
      default:
        throw new QueryValidationException("CQL: Unknown comparator '" + comparator + "'");
    }
    String sql = fld + " " + comparator + " '" + FTTerm(node.getTerm()) + "'";
    // Quote escaping? Truncation? Special characters?
    // Should not use full FTTerm, it does truncation etc in a funny way
    logger.log(Level.FINE, "pgFT():  sql={0} in={1} js={2}",
      new Object[]{sql, fld, this.jsonField});
    return sql;
  }

  /**
   * Handle a subquery. For example when searching an item, by a material type
   * name.
   *
   * cql: materialtype.name = "book"
   *
   * sql: materialtypeid in (select id from materialtype where name = "book")
   *
   * @param node
   * @param index
   * @return
   * @throws QueryValidationException
   */
  private String subQuery1FT(CQLTermNode node, String index) throws QueryValidationException {
    //System.out.println("CQL2PgJSON.subQuery1FT() starting: " + node.toCQL())
    String[] idxParts = index.split("\\.");
    if (idxParts.length != 2) {
      logger.log(Level.SEVERE, "subQuery1FT(): needs two-part index name, not ''{0}''", index);
      return null;
    }
    if (!dbTable.has("foreignKeys")) {
      logger.log(Level.SEVERE, "subQuery1FT(): No foreign keys defined for ''{0}''", dbTable.getString("tableName"));
      return null;
    }
    JSONObject fkey = findItem(dbTable.getJSONArray("foreignKeys"),
      "targetTable", idxParts[0]);
    //System.out.println("CQL2PgJSON.subQuery1FT(): Found foreignKey '" + fkey)

    if (fkey == null) {
      logger.log(Level.SEVERE, "subQuery1FT(): No foreignKey ''{0}'' found", idxParts[0]);
      return null;
    }
    if (!fkey.has("fieldName") || !fkey.has("targetTable")) {
      logger.log(Level.SEVERE, "subQuery1FT(): Malformed foreignKey section {0}", fkey);
      return null;
    }
    String fkField = fkey.getString("fieldName"); // tagId
    String fkTable = fkey.getString("targetTable");  // tags
    try {
      // This is nasty. Make a new constructor that takes the dbSchema as a JsonObject,
      // so we don't need to convert back and forth! Also, get the .jsonb right!
      CQL2PgJSON c = new CQL2PgJSON(fkTable + ".jsonb", dbSchema.toString());
      String term = node.getTerm();
      if (term.isEmpty()) {
        term = "\"\"";
      }
      String subCql = idxParts[1] + " " + node.getRelation() + " " + term;
      //System.out.println("CQL2PgJSON.subQuery1FT() sub cql: " + subCql)
      String subSql = c.cql2pgJson(subCql);
      //System.out.println("CQL2PgJSON.subQuery1FT() sub sql: " + subSql)
      String fld = index2sqlText(this.jsonField, fkField);
      String sql = fld + " in ( SELECT jsonb->>'id' from " + fkTable
        + " WHERE " + subSql + " )";
      //System.out.println("CQL2PgJSON.subQuery1FT() sql: " + sql)
      return sql;
    } catch (IOException | FieldException | QueryValidationException | SchemaException e) {
      // We should not get these exceptions, as we construct a valid query above,
      // using a valid schema.
      logger.log(Level.SEVERE, "subQuery1FT() Caught an exception", e);
      return null;
    }
  }

  // Handle a subquery the other way around. For example when searching a
  // material type by the items that refer to it.
  // cql: item.author = foo
  // sql: id in ( select item.materialtypeId from item where author = foo )
  private String subQuery2FT(CQLTermNode node, String index) throws QueryValidationException {
    //System.out.println("CQL2PgJSON.subQuery2FT() starting: " + node.toCQL())
    String[] idxParts = index.split("\\.", 2);
    if (idxParts.length != 2) {
      logger.log(Level.SEVERE, "subQuery2FT(): needs two-part index name, not {0}", index);
      return null;
    }
    // find foreign keys in the other table that refer to the current table, and
    // have an index on the field name
    JSONObject table = findItem(dbSchema.getJSONArray("tables"), "tableName", idxParts[0]);
    if (table == null) {
      logger.log(Level.SEVERE, "subQueryFT(): Table {0} not found", idxParts[0]);
      return null;
    }
    //System.out.println("CQL2PgJSON.subQueryFT(): Found table " + table)

    if (!table.has("foreignKeys")) {
      logger.log(Level.SEVERE, "subQueryFT(): No foreign keys defined for {0}", idxParts[0]);
      return null;
    }

    String mainTable = this.dbTable.getString("tableName");
    JSONObject fkey = findItem(table.getJSONArray("foreignKeys"),
      "targetTable", mainTable);
    //System.out.println("CQL2PgJSON.subQueryFT(): Found foreignKey '" + fkey)

    if (fkey == null) {
      logger.log(Level.SEVERE, "subQueryFT(): No foreignKey ''{0}'' found", idxParts[0]);
      return null;
    }

    if (!fkey.has("fieldName") || !fkey.has("targetTable")) {
      logger.log(Level.SEVERE, "subQueryFT(): Malformed foreignKey section {0}", fkey);
      return null;
    }
    String fkField = fkey.getString("fieldName"); // tagId
    try {
      // This is nasty. Make a new constructor that takes the dbSchema as a JsonObject,
      // so we don't need to convert back and forth! Also, get the .jsonb right!
      CQL2PgJSON c = new CQL2PgJSON(idxParts[0] + ".jsonb", dbSchema.toString());
      String term = node.getTerm();
      // We may need to be more specific about quoting! Even better, do not pass as string
      if (term.isEmpty()) {
        term = "\"\"";
      }
      String subCql = idxParts[1] + " " + node.getRelation() + " " + term;
      //System.out.println("CQL2PgJSON.subQueryFT() sub cql: " + subCql)
      String subSql = c.cql2pgJson(subCql);
      //System.out.println("CQL2PgJSON.subQueryFT() sub sql: " + subSql)
      String fld = index2sqlText(c.jsonField, fkField);
      String sql = " jsonb->>'id' in ( SELECT " + fld + " from " + idxParts[0]
        + " WHERE " + subSql + " )";
      //System.out.println("CQL2PgJSON.subQueryFT() sql: " + sql)
      return sql;
    } catch (IOException | FieldException | QueryValidationException | SchemaException e) {
      // We should not get these exceptions, as we construct a valid query above,
      // using a valid schema.
      logger.log(Level.SEVERE, "subQueryFT() Caught an exception{0}", e);
      return null;
    }
  }

}
