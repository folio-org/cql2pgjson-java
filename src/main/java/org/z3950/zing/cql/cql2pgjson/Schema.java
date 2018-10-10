package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.File;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Resolves index names against a JSON schema. Can return the fully qualified
 * index and the cqlRelation for it.
 */
public class Schema {

  /* Private use variables and object structures*/
  private static final String PROPERTIES = "properties";
  private static final String TYPE = "type";
  private static final String ITEMS = "items";
  private static final String REF = "$ref";
  private static final String ITEMS_USAGE_MESSAGE
    = "`items` is a reserved field name, whose value should be a Json object containing `type` field.";
  private static final int MIN_DEPTH = 4;

  /**
   * Container for path and RAML type of a field.
   */
  public static class Field {

    private final String path;
    private final String type;

    Field(String path, String type) {
      this.path = path;
      if (type == null) {
        this.type = "";
      } else {
        this.type = type;
      }
    }

    /**
     * The full path of this field.
     *
     * @return full path
     */
    String getPath() {
      return path;
    }

    /**
     * RAML type like integer, number, string, boolean, datetime, ... "" for
     * unknown.
     *
     * @return the RAML type.
     */
    String getType() {
      return type;
    }
  }

  private static class JsonPath {

    String path = null;
    String type = null;
    String items = null;
  }

  private String schemaJsonString = null;

  /* End of private use variables and object structures*/
  /**
   * Load JSON schema
   *
   * @throws IOException on parse failure
   * @throws SchemaException if the schema in not well formed
   */
  public Schema(String schemaJson) throws IOException, SchemaException {
    schemaJsonString = schemaJson;
    JsonFactory jsonFactory = new JsonFactory();
    jsonFactory.createParser(schemaJson);
  }

  private Field matchLeaf(String index, String iType, Deque<String> path, String type) {
    String pathP = String.join(".", path);
    if (pathP.endsWith(index) && (iType == null || iType.endsWith(type))) {
      return new Field(pathP, type);
    }
    return null;
  }

  private Field recurseItems(String index, String iType, Deque<String> path, JsonParser jp)
    throws IOException, SchemaException, QueryValidationException {
    Field field = null;
    JsonToken jt = jp.nextToken();
    String type = null;
    boolean gotProperties = false;
    if (!jt.equals(JsonToken.START_OBJECT)) {
      throw new SchemaException(ITEMS_USAGE_MESSAGE);
    }
    while (!jp.isClosed() && !jt.equals(JsonToken.END_OBJECT)) {
      jt = jp.nextToken();
      if (jt.equals(JsonToken.FIELD_NAME)) {
        Field f = null;
        switch (jp.getCurrentName()) {
          case TYPE:
            jp.nextValue();
            type = jp.getValueAsString();
            break;
          case PROPERTIES:
            f = recurseProperties(index, iType, path, jp);
            gotProperties = true;
            break;
          case REF:
            jp.nextToken();
            f = recurseRef(index, iType, path, jp.getValueAsString());
            gotProperties = true;
            break;
          default:
          // ignore
        }
        if (f != null) {
          field = f;
        }
      }
    }
    if (!gotProperties) {
      field = matchLeaf(index, iType, path, type);
    }
    return field;
  }

  private Field recurseRef(String index, String iType, Deque<String> path, String refVal)
    throws IOException, SchemaException, QueryValidationException {

    if (path.size() > MIN_DEPTH && String.join(".", path).length() > index.length()) {
      return null;
    }
    if (!refVal.startsWith("file")) {
      throw new IOException("$ref: Cannot resolve path " + refVal);
    }
    refVal = refVal.replace(File.separator, "/");
    int idx = -1;
    if (idx == -1) {
      final String lead1 = "target/test-classes/";
      idx = refVal.indexOf(lead1);
      if (idx != -1) {
        idx += lead1.length();
      }
    }
    if (idx == -1) {
      final String lead2 = "target/classes/";
      idx = refVal.indexOf(lead2);
      if (idx != -1) {
        idx += lead2.length();
      }
    }
    if (idx == -1) {
      throw new IOException("$ref: Cannot resolve path " + refVal);
    }
    final String resourceName = refVal.substring(idx);
    URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
    if (url == null) {
      throw new IOException("$ref: Cannot get resource for " + resourceName);
    }

    JsonFactory jsonFactory = new JsonFactory();
    JsonParser jp = jsonFactory.createParser(url);
    return recurseTop(index, iType, path, jp);
  }

  private Field recurseProperty(String index, String iType,
    Deque<String> path, JsonParser jp) throws IOException, SchemaException, QueryValidationException {
    Field field = null;
    String fieldName = jp.getCurrentName();
    path.addLast(fieldName);
    String type = null;
    boolean gotProperties = false;
    boolean gotItems = false;
    while (!jp.isClosed()) {
      JsonToken jt = jp.nextToken();
      if (jt == null || jt.equals(JsonToken.END_OBJECT)) {
        break;
      }
      if (jt.equals(JsonToken.FIELD_NAME)) {
        Field f = null;
        switch (jp.getCurrentName()) {
          case PROPERTIES:
            f = recurseProperties(index, iType, path, jp);
            gotProperties = true;
            break;
          case TYPE:
            jp.nextToken();
            type = jp.getValueAsString();
            break;
          case ITEMS:
            f = recurseItems(index, iType, path, jp);
            gotItems = true;
            break;
          case REF:
            jp.nextToken();
            f = recurseRef(index, iType, path, jp.getValueAsString());
            gotProperties = true;
            break;
          default:
          // ignore
        }
        if (f != null) {
          if (field != null) {
            throw new QueryAmbiguousException("Field name \'" + index + "\' is ambiguous");
          }
          field = f;
        }
      }
    }
    if (!gotItems && !gotProperties) {
      field = matchLeaf(index, iType, path, type);
    }
    path.removeLast();
    return field;
  }

  private Field recurseProperties(String index, String iType, Deque<String> path, JsonParser jp)
    throws IOException, SchemaException, QueryValidationException {
    Field field = null;
    while (!jp.isClosed()) {
      JsonToken jt = jp.nextToken();
      if (jt == null || jt.equals(JsonToken.END_OBJECT)) {
        break;
      }
      if (jt.equals(JsonToken.FIELD_NAME)) {
        Field f = recurseProperty(index, iType, path, jp);
        if (f != null) {
          if (field != null) {
            throw new QueryAmbiguousException("Field name \'" + index + "\' is ambiguous");
          }
          field = f;
        }
      }
    }
    return field;
  }

  private Field recurseTop(String index, String iType, Deque<String> path, JsonParser jp)
    throws QueryValidationException, IOException, SchemaException {
    while (!jp.isClosed()) {
      JsonToken jt = jp.nextToken();
      if (jt == null) {
        break;
      }
      if (jt.equals(JsonToken.FIELD_NAME)
        && jp.getCurrentName().equals(PROPERTIES)) {
        Field f = recurseProperties(index, iType, path, jp);
        if (f != null) {
          return f;
        }
      }
    }
    return null;
  }

  /**
   * Confirm that a particular field is valid and unambiguous for a given
   * schema. Returns a Field with type and the fully-specified version of the
   * (possibly abbreviated) field name. For example, looking up 'zip' may return
   * 'address.zip' as path.
   *
   * @param index field name, may be abbreviated
   * @return type and path of the field.
   * @throws QueryValidationException on ambiguous index
   */
  public Field mapFieldNameAgainstSchema(String index) throws QueryValidationException {
    return mapFieldNameAndTypeAgainstSchema(index, null);
  }

  /**
   * Confirm that a particular field is valid and unambiguous for a given
   * schema. The return value with be the fully-specified version of the
   * (possibly abbreviated) field name. For example, looking up 'zip' may return
   * 'address.zip'. Any fields in the index that don't match the specified type
   * will not be considered to be matches. Currently, a type argument of
   * 'string' will match an index field of type 'string' or an array of type
   * 'string'.
   *
   * @param index field name, may be abbreviated
   * @param type type of index
   * @return fully-specified version of field value.
   * @throws QueryValidationException if index and type is not found or
   * ambiguous
   */
  public Field mapFieldNameAndTypeAgainstSchema(String index, String iType) throws QueryValidationException {
    try {
      Deque<String> path = new ArrayDeque<>();
      JsonFactory jsonFactory = new JsonFactory();
      JsonParser jp = jsonFactory.createParser(schemaJsonString);
      Field field = recurseTop(index, iType, path, jp);
      if (field == null) {
        throw queryValidationException(index, null);
      }
      if (iType != null && !iType.equals(field.type)) {
        throw queryValidationException(index, iType);
      }
      return field;
    } catch (IOException ex) {
      System.out.println("IOException ex=" + ex.getLocalizedMessage());
      return null;
    } catch (SchemaException ex) {
      System.out.println("SchemaException ex=" + ex.getLocalizedMessage());
      return null;
    }
  }

  /**
   * Create a QueryValidationExceptionn for index and type that are not found.
   *
   * @param index index
   * @param type type (may be null)
   * @return the exception
   */
  private QueryValidationException queryValidationException(String index, String type) {
    return new QueryValidationException(
      "Field name '" + index
      + (type == null ? "" : "' with type '" + type)
      + "' is not present in index.");
  }
}
