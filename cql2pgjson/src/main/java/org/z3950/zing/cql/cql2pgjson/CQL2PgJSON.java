package org.z3950.zing.cql.cql2pgjson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;


import org.apache.commons.lang3.StringUtils;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.cql2pgjson.exception.QueryValidationException;
import org.folio.cql2pgjson.exception.ServerChoiceIndexesException;
import org.folio.cql2pgjson.model.CqlAccents;
import org.folio.cql2pgjson.model.CqlCase;
import org.folio.cql2pgjson.model.CqlMasking;
import org.folio.cql2pgjson.model.CqlModifiers;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.cql2pgjson.model.CqlTermFormat;
import org.folio.cql2pgjson.model.DbIndex;
import org.folio.cql2pgjson.model.IndexTextAndJsonValues;
import org.folio.cql2pgjson.model.SqlSelect;
import org.folio.cql2pgjson.util.Cql2SqlUtil;
import org.folio.cql2pgjson.util.DbSchemaUtils;
import org.folio.rest.persist.ddlgen.Schema;
import org.folio.rest.tools.utils.ObjectMapperTool;
import org.json.JSONArray;
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
@SuppressWarnings("squid:S1192")  // We have a few duplicated strings.
// I refuse to harm readability by declaring symbolic names for them.
public class CQL2PgJSON {

  /**
   * Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
   * Must conform to SQL identifier requirements (characters, not a keyword), or properly
   * quoted using double quotes.
   */
  private static Logger logger = Logger.getLogger(CQL2PgJSON.class.getName());

  private String jsonField = null;
  private List<String> jsonFields = null;

  private JSONObject dbSchema; // The whole schema.json, with all tables etc
  private JSONObject dbTable; // Our primary table inside the dbSchema

  public JSONObject getDbSchema() {
    return dbSchema;
  }

  public JSONObject getDbTable() {
    return dbTable;
  }

  // leverage RMB and consider to merge cql2pgjson into RMB
  private Schema dbSchemaObject;

  /**
   * Default index names to be used for cql.serverChoice.
   * May be empty, but not null. Must not contain null, names must not contain double quote or single quote.
   */
  private List<String> serverChoiceIndexes = Collections.emptyList();

  private JSONObject loadDbSchema() {
    return loadDbSchema(null);
  }

  private JSONObject loadDbSchema(String schemaPath) {
    try {
      String dbJson;
      if(schemaPath == null) {
        ClassLoader classLoader = CQL2PgJSON.class.getClassLoader();
        InputStream resourceAsStream = classLoader.getResourceAsStream("templates/db_scripts/schema.json");
        if (resourceAsStream == null) {
          logger.log(Level.SEVERE, "loadDbSchema failed to load resource 'templates/db_scripts/schema.json'");
          return null;
        }
        dbJson = IOUtils.toString(resourceAsStream, "UTF-8");
        logger.log(Level.INFO, "loadDbSchema: Loaded 'templates/db_scripts/schema.json' OK");
      } else {
        File jsonFile = new File(schemaPath);
        dbJson = FileUtils.readFileToString(jsonFile, Charset.forName("UTF-8"));
        logger.log(Level.INFO, "loadDbSchema: Loaded " + schemaPath + " OK");
      }

      try {
        dbSchemaObject = ObjectMapperTool.getMapper().readValue(dbJson, org.folio.rest.persist.ddlgen.Schema.class);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Cannot convert db schema defintion to Java object");
      }

      return new JSONObject(dbJson);
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "No schema.json found", ex);
    }
    return null;
  }

  private void initDbTable() {
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

  private void doInit(String field, String dbSchemaPath) throws FieldException {
    this.jsonField = trimNotEmpty(field);
    if(dbSchemaPath != null) {
      dbSchema = loadDbSchema(dbSchemaPath);
    } else {
      dbSchema = loadDbSchema();
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
    doInit(field, null);
    initDbTable();
  }

  /**
   * Allow to use customized dbSchemaPath
   *
   * @param dbSchemaPath
   */
  public void setDbSchema(String dbSchemaPath) {
    this.dbSchema = loadDbSchema(dbSchemaPath);
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
   * Create an instance for the specified list of schemas. If only one field name is provided, queries will
   * default to the handling of single field queries.
   *
   * @param fields Field names of the JSON fields, may include schema and table name (e.g. tenant1.user_table.json).
   *  Must conform to SQL identifier requirements (characters, not a keyword), or properly quoted using double quotes.
   *  The first field name on the list will be the default field for terms in queries that don't specify a json field.
   * @throws FieldException (subclass of CQL2PgJSONException) - provided field is not valid
   */
  public CQL2PgJSON(List<String> fields) throws FieldException {
    dbSchema = loadDbSchema();
    if (fields == null || fields.isEmpty())
      throw new FieldException( "fields list must not be empty" );
    this.jsonFields = new ArrayList<>();
    for (String field : fields) {
      this.jsonFields.add(trimNotEmpty(field));
    }
    if (this.jsonFields.size() == 1) {
      this.jsonField = this.jsonFields.get(0);
    }
    initDbTable();
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

      // if sort field is marked explicitly as number type
      if (modifiers.cqlTermFormat == CqlTermFormat.NUMBER) {
        order.append(vals.indexJson).append(desc);
        continue;
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

    // special case for the query the UI uses most often, before the user has
    // typed in anything: title=* OR contributors*= OR identifier=*
    if ("OR".equals(operator)
      && node.getRightOperand().getClass() == CQLTermNode.class) {
      CQLTermNode r = (CQLTermNode) (node.getRightOperand());
      if ("*".equals(r.getTerm()) && "=".equals(r.getRelation().getBase())) {
        logger.log(Level.FINE, "pgFT(): Simplifying =* OR =* ");
        return pg(node.getLeftOperand());
      }
    }

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
   * Return SQL full text search expression for an "Adj/And/Any" CQL match string.
   *
   * @param textIndex  JSONB field to match against
   * @param modifiers  CqlModifiers to use
   * @param cqlTerm  words to convert
   * @param ftOperator full text search operator to use. One of "<->", "&", or "|"
   *
   * @return resulting full text search expression
   * @throws QueryValidationException
   */
  private static String [] pgFtAAA(String textIndex, CqlModifiers modifiers, String cqlTerm, String ftOperator) throws QueryValidationException {

    logger.warning("Doing full text search without index: " + textIndex + ftOperator + cqlTerm);

    String [] split = cqlTerm.trim().split("\\s+");  // split at whitespace
    if (split.length == 1 && ("".equals(split[0]) || "*".equals(split[0]))) {
      // The variable cql contains whitespace only. honorWhitespace is not implemented yet.
      // So there is no word at all. Therefore no restrictions for matching - anything matches.
      return new String [] { textIndex + " ~ ''" };  // matches any (existing non-null) value
      // don't use "TRUE" because that also matches when the field is not defined or is null
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < split.length; i++) {
      split[i] = fTTerm(split[i]);
    }
    String tsTerm = String.join(ftOperator, split);
    sb.append("to_tsvector('simple', f_unaccent(" + textIndex + ")) "
      + "@@ to_tsquery('simple', f_unaccent('" + tsTerm + "'))");

    // Compensate full text not supported modifiers for now. Will change in CQLPG-81
    if (modifiers.cqlAccents == CqlAccents.RESPECT_ACCENTS || modifiers.cqlCase == CqlCase.RESPECT_CASE) {
      logger.warning("Trying to compensate unsupported modifiers: " + modifiers.cqlAccents + " : " + modifiers.cqlCase);
      sb.append(" AND (");
      String relOp = "|".equals(ftOperator) ? " OR " : " AND ";
      for (int i = 0; i < split.length; i++) {
        sb.append(wrapInLowerUnaccent(textIndex, modifiers) +
          " LIKE '%' || " + wrapInLowerUnaccent("'" + split[i] + "'", modifiers) + " || '%'")
          .append(i < split.length - 1 ? relOp : "");
      }
      sb.append(")");
    }

    return new String[] { sb.toString() };
  }

  private static String [] match(String textIndex, CQLTermNode node) throws QueryValidationException {
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
    case "=":   // use "adj"
    case "adj":
      return pgFtAAA(textIndex, modifiers, node.getTerm(), "<->");
    case "all":
      return pgFtAAA(textIndex, modifiers, node.getTerm(), "&");
    case "any":
      return pgFtAAA(textIndex, modifiers, node.getTerm(), "|");
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
    vals.indexJson = index2sqlJson(this.jsonField, index);
    vals.indexText = index2sqlText(this.jsonField, index);
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

  // TODO: PG-81 new stuff
  private String index2sql(String index, CQLTermNode node) throws QueryValidationException {

    // special handling of id search (re-use existing code)
    if ("id".equals(index)) {
      return pgId(node);
    }

    IndexTextAndJsonValues vals = getIndexTextAndJsonValues(index);
    DbIndex dbIndex = DbSchemaUtils.getDbIndex(dbSchemaObject, vals.indexJson);

    CqlModifiers modifiers = new CqlModifiers(node);
    String comparator = node.getRelation().getBase().toLowerCase();

    switch (comparator) {
    case "=":
      if (CqlTermFormat.NUMBER == modifiers.cqlTermFormat) {
        return queryBySql(dbIndex.other, vals, node, comparator, modifiers);
      } else if (CqlAccents.IGNORE_ACCENTS == modifiers.cqlAccents &&
          CqlCase.IGNORE_CASE == modifiers.cqlCase) {
        return queryByFt(dbIndex.ft, vals, node, comparator, modifiers);
      } else {
        return queryByLike(dbIndex.gin, vals, node, comparator, modifiers);
      }
    case "adj":
    case "all":
    case "any":
      return queryByFt(dbIndex.ft, vals, node, comparator, modifiers);
    case "==":
    case "<>":
      if (CqlTermFormat.STRING == modifiers.cqlTermFormat) {
        return queryByLike(dbIndex.gin, vals, node, comparator, modifiers);
      }
    case "<" :
    case ">" :
    case "<=" :
    case ">=" :
      return queryBySql(dbIndex.other, vals, node, comparator, modifiers);
    default:
      throw new CQLFeatureUnsupportedException("Relation " + comparator
          + " not implemented yet: " + node.toString());
    }
  }

  private String queryByFt(boolean hasFtIndex, IndexTextAndJsonValues vals, CQLTermNode node, String comparator, CqlModifiers modifiers) throws QueryValidationException {

    String index = vals.indexText;

    if (!hasFtIndex) {
      logger.log(Level.WARNING, "Doing FT search without FT index " + index);
    }

    if (CqlAccents.RESPECT_ACCENTS == modifiers.cqlAccents) {
      logger.log(Level.WARNING, "Ignoring /respectAccents modifier for FT search " + index);
    }

    if (CqlCase.RESPECT_CASE == modifiers.cqlCase) {
      logger.log(Level.WARNING, "Ignoring /respectCase modifier for FT search " + index);
    }

    // Clean the term. Remove stand-alone ' *', not valid word.
    String term = node.getTerm().replaceAll(" +\\*", "").trim();
    if (term.equals("*")) {
      return "true";
    }
    if (term.equals("")) {
      return index + " ~ ''";
    }
    String[] words = term.split("\\s+");
    for (int i = 0; i < words.length; i++) {
      words[i] = fTTerm(words[i]);
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
//      case "<>":
//        tsTerm = "!(" + String.join("<->", words) + ")";
//        break;
      default:
        throw new QueryValidationException("CQL: Unknown comparator '" + comparator + "'");
    }
    // "simple" dictionary only does to_lowercase, so need f_unaccent
    String sq = "to_tsvector('simple', f_unaccent(" + index + ")) "
      + "@@ to_tsquery('simple', f_unaccent('" + tsTerm + "'))";

    //TODO: remove
    logger.log(Level.WARNING, "index " + index + " generated SQL: " + sq);
    return sq;
  }

  private String queryByLike(boolean hasGinIndex, IndexTextAndJsonValues vals, CQLTermNode node, String comparator, CqlModifiers modifiers) {

    String index = vals.indexText;

    if (!hasGinIndex) {
      logger.log(Level.WARNING, "Doing LIKE search without GIN index for " + index);
    }

    String likeOperator = comparator.equals("<>") ? " NOT LIKE " : " LIKE ";
    String like = "'" + Cql2SqlUtil.cql2like(node.getTerm()) + "'";
    String indexMatch = wrapInLowerUnaccent(index) + likeOperator + wrapInLowerUnaccent(like);
    String sql = null;
    if (modifiers.cqlAccents == CqlAccents.IGNORE_ACCENTS && modifiers.cqlCase == CqlCase.IGNORE_CASE) {
      sql = indexMatch;
    } else {
      sql = indexMatch + " AND " +
        wrapInLowerUnaccent(index, modifiers) + likeOperator + wrapInLowerUnaccent(like, modifiers);
    };

    // TODO: remove later
    logger.log(Level.WARNING, "index " + index + " generated SQL: " + sql);
    return sql;
  }

  private String queryBySql(boolean hasIndex, IndexTextAndJsonValues vals, CQLTermNode node, String comparator, CqlModifiers modifiers) {
    if (comparator.equals("==")) {
      comparator = "=";
    }
    String index = vals.indexText;
//    String term = "'" + node.getTerm().replace("'", "''") + "'";
    String term = "'" + Cql2SqlUtil.cql2like(node.getTerm()) + "'";
    if (CqlTermFormat.NUMBER.equals(modifiers.cqlTermFormat)) {
      index = "(" + index + ")::numeric";
      term = node.getTerm();
    }
    String sql = index + " " + comparator + term;

    // TODO: remove later
    logger.log(Level.WARNING, "index " + index + " generated SQL: " + sql);
    return sql;
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
  private String index2sqlOld(String index, CQLTermNode node, String jsonMatch) throws QueryValidationException {

    if (dbTable != null) {  // we have schema.json, and can look up indexes.
      // Full text indexes
      JSONObject ftIndex = null;
      if (dbTable.has("fullTextIndex")) {
        JSONArray ftIndexes = dbTable.getJSONArray("fullTextIndex");
        ftIndex = findItem(ftIndexes, "fieldName", index);
        if (ftIndex != null) {
          return pgFT(node, ftIndex, index);
        }
      }
      // Special handling for id queries
      if ("id".equals(index)) {
        return pgId(node);
      }
    }

    IndexTextAndJsonValues vals = getIndexTextAndJsonValues(index);

    if (jsonMatch != null && isNumberComparator(node)) {
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
        String indexTermWithinField = index.substring(f.length()+1);
        vals.indexJson = index2sqlJson(f, indexTermWithinField);
        vals.indexText = index2sqlText(f, indexTermWithinField);
        return vals;
      }
    }

    // if no json field name prefix is found, the default field name gets applied.
    String defaultJsonField = this.jsonFields.get(0);
    vals.indexJson = index2sqlJson(defaultJsonField, index);
    vals.indexText = index2sqlText(defaultJsonField, index);
    return vals;
  }

  private String pg(CQLTermNode node) throws QueryValidationException {
    if ("cql.allRecords".equalsIgnoreCase(node.getIndex())) {
      return "true";
    }
    String numberMatch = getNumberMatch(node);
    if ("cql.serverChoice".equalsIgnoreCase(node.getIndex())) {
      if (serverChoiceIndexes.isEmpty()) {
        throw new QueryValidationException("cql.serverChoice requested, but no serverChoiceIndexes defined.");
      }
      List<String> sqlPieces = new ArrayList<>();
      for(String index : serverChoiceIndexes) {
//        sqlPieces.add(index2sql(index, node, numberMatch));
        sqlPieces.add(index2sql(index, node));
      }
      return String.join(" OR ", sqlPieces);
    }
//    return index2sql(node.getIndex(), node, numberMatch);
    return index2sql(node.getIndex(), node);
  }

  /**
   * Normalize a term for FT searching. Escape quotes, masking, etc
   *
   * @param term
   * @return
   */
  @SuppressWarnings({
    "squid:ForLoopCounterChangedCheck",
    // Yes, we skip the occasional character in the loop by incrementing i
    "squid:S135"
  // Yes, we have a few continue statements. Unlike what SQ says,
  // refactoring the code to avoid that would make it much less
  // readable.
  })
  private static String fTTerm(String term) throws QueryValidationException {
    StringBuilder res = new StringBuilder();
    term = term.trim();
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
        default:
        // SQ complains if there is no default case, and if there is an empty statement #!$
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
   * Handle a termnode that does a search on the id. We use the primary key
   * column in the query, it is clearly faster, and we use a numerical
   * comparison instead of truncation. That way PG will use the primary key,
   * which is pretty much faster. Assumes that the UUID has already been
   * validated to be in the right format.
   *
   * @param node
   * @return SQL where clause component for this term
   * @throws QueryValidationException
   */
  private String pgId(CQLTermNode node) throws QueryValidationException {
    final String uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    String pkColumnName = dbTable.optString("pkColumnName", /* default = */ "id");
    String comparator = StringUtils.defaultString(node.getRelation().getBase());
    if (!node.getRelation().getModifiers().isEmpty()) {
      throw new QueryValidationException("CQL: Unsupported modifier "
        + node.getRelation().getModifiers().get(0).getType());
    }
    boolean equals = true;
    switch (comparator) {
    case "==":
    case "=":
      comparator = "=";
      break;
    case "<>":
      equals = false;
      break;
    default:
      throw new QueryValidationException("CQL: Unsupported operator '" + comparator + "' "
          + "id only supports '=', '==', and '<>' (possibly with right truncation)");
    }
    String term = node.getTerm();
    if (term.equals("") || term.equals("*")) {
      return equals ? "true" : "false";  // no need to check
      // not even for "", since id is a mandatory field, so
      // "all that have id" is the same as "all records"
    }

    if (!term.contains("*")) { // exact match
      if (!term.matches(uuidPattern)) {
        // avoid SQL injection, don't put term into comment
        return equals
            ? "false /* id == invalid UUID */"
            : "true /* id <> invalid UUID */";
      }
      return pkColumnName + comparator + "'" + term + "'";
    }
    String truncTerm = term.replaceFirst("\\*$", ""); // remove trailing '*'
    if (truncTerm.contains("*")) { // any remaining '*' is an error
      throw new QueryValidationException("CQL: only right truncation supported for id:  " + term);
    }
    String lo = new StringBuilder("00000000-0000-0000-0000-000000000000")
      .replace(0, truncTerm.length(), truncTerm).toString();
    String hi = new StringBuilder("ffffffff-ffff-ffff-ffff-ffffffffffff")
      .replace(0, truncTerm.length(), truncTerm).toString();
    if (!lo.matches(uuidPattern) || !hi.matches(uuidPattern)) {
      // avoid SQL injection, don't put term into comment
      return equals ? "false /* id == invalid UUID */"
                    : "true /* id <> invalid UUID */";
    }
    if (equals) {
      return "(" + pkColumnName + ">='" + lo + "'"
        + " and " + pkColumnName + "<='" + hi + "')";
    } else {
      return "(" + pkColumnName + "<'" + lo + "'"
          + " or " + pkColumnName + ">'" + hi + "')";
    }
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
      // Clean the term. Remove stand-alone ' *', not valid word.
      String term = node.getTerm().replaceAll(" +\\*", "").trim();
      // Special cases ="" and ="*"
      if (comparator.equals("=") || comparator.equals("adj")
        || comparator.equals("any") || comparator.equals("all")) {
        if (term.equals("*")) {  // Plain "*" means all records.
          String sql = "true";
          logger.log(Level.FINE, "pgFT(): special case: plain '*' ''='' {0}", sql);
          return sql;
        }
        if (term.equals("")) {
          String sql = fld + " ~ ''";
          logger.log(Level.FINE, "pgFT(): special case: empty term ''='' {0}", sql);
          return sql;
        }
      }
      String[] words = term.trim().split("\\s+");  // split at whitespace
      for (int i = 0; i < words.length; i++) {
        words[i] = fTTerm(words[i]);
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
        default:
          throw new QueryValidationException("CQL: Unknown comparator '" + comparator + "'");
      }
      logger.log(Level.FINE, "pgFT(): term={0} ts={1}",
        new Object[]{node.getTerm(), tsTerm});
      return "to_tsvector('simple', " + fld + ") "
        + "@@ to_tsquery('simple','" + tsTerm + "')";
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
    String sql = fld + " " + comparator + " '" + fTTerm(node.getTerm()) + "'";
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
      CQL2PgJSON c = new CQL2PgJSON(fkTable + ".jsonb");
      String term = node.getTerm();
      if (term.isEmpty()) {
        term = "\"\"";
      }
      String subCql = idxParts[1] + " " + node.getRelation() + " " + term;
      //System.out.println("CQL2PgJSON.subQuery1FT() sub cql: " + subCql)
      String subSql = c.cql2pgJson(subCql);
      //System.out.println("CQL2PgJSON.subQuery1FT() sub sql: " + subSql)
      String fld = index2sqlText(this.jsonField, fkField);
      return fld + " in ( SELECT jsonb->>'id' from " + fkTable
        + " WHERE " + subSql + " )";
      //System.out.println("CQL2PgJSON.subQuery1FT() sql: " + sql)
    } catch (FieldException | QueryValidationException e) {
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
      CQL2PgJSON c = new CQL2PgJSON(idxParts[0] + ".jsonb");
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
      return " jsonb->>'id' in ( SELECT " + fld + " from " + idxParts[0]
        + " WHERE " + subSql + " )";
      //System.out.println("CQL2PgJSON.subQueryFT() sql: " + sql)
    } catch (FieldException | QueryValidationException e) {
      // We should not get these exceptions, as we construct a valid query above,
      // using a valid schema.
      logger.log(Level.SEVERE, "subQueryFT() Caught an exception{0}", e);
      return null;
    }
  }

}
