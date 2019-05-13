package org.folio.cql2pgjson.tbd;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.net.URLDecoder;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

import org.folio.cql2pgjson.exception.QueryValidationException;

import java.util.logging.Level;

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
  private static Logger logger = Logger.getLogger(Schema.class.getName());

  /**
   * Container for path and RAML type of a field.
   */
  public static class Field {

    private final String path;
    private final String type;

    public Field(String path, String type) {
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
    public String getPath() {
      return path;
    }

    /**
     * RAML type like integer, number, string, boolean, datetime, ... "" for
     * unknown.
     *
     * @return the RAML type.
     */
    public String getType() {
      return type;
    }
  }

  private String schemaJsonString = null;

  /* End of private use variables and object structures*/

  /**
   * Load JSON schema
   *
   * @param schemaJson  JSON schema describing name, path and type of allowed fields.
   * @throws IOException on parse failure
   * @throws SchemaException if the schema in not well formed
   */
  public Schema(String schemaJson) throws IOException, SchemaException {
    schemaJsonString = schemaJson;
    JsonFactory jsonFactory = new JsonFactory();
    jsonFactory.createParser(schemaJson);
  }

  private Field matchLeaf(Field field, String index, String iType, Deque<String> path, String type)
      throws QueryValidationException {

    String pathP = String.join(".", path);
    if (field != null) {
      if ("array".equals(type) && field.getPath().length() > pathP.length()) {
        throw new QueryValidationException("A subfield of an array is not allowed. " +
            field.getPath() + " is subfield of array " + pathP);
      }
      return field;
    }

    if (! pathP.equals(index)) {
      return null;
    }
    if ("array".equals(type)) {
      if (iType != null && !iType.endsWith("string")) {
        return null;
      }
      return new Field(pathP, "string");
    }
    if (iType != null && !iType.endsWith(type)) {
      return null;
    }
    return new Field(pathP, type);
  }

  private Field recurseItems(String index, String iType, Deque<String> path, JsonParser jp)
    throws IOException, SchemaException, QueryValidationException, URISyntaxException {
    Field field = null;
    JsonToken jt = jp.nextToken();
    String type = null;
    if (!jt.equals(JsonToken.START_OBJECT)) {
      throw new SchemaException(ITEMS_USAGE_MESSAGE);
    }
    while (!jp.isClosed() && !jt.equals(JsonToken.END_OBJECT)) {
      jt = jp.nextToken();
      if (jt.equals(JsonToken.FIELD_NAME)) {
        switch (jp.getCurrentName()) {
          case TYPE:
            jp.nextValue();
            type = jp.getValueAsString();
            break;
          case PROPERTIES:
            field = recurseProperties(index, iType, path, jp);
            break;
          case REF:
            jp.nextToken();
            field = recurseRef(index, iType, path, jp.getValueAsString());
            break;
          default:
          // ignore
        }
      }
    }
    field = matchLeaf(field, index, iType, path, type);
    return field;
  }

  private Field recurseRef(String index, String iType, Deque<String> path, String refVal)
    throws IOException, SchemaException, QueryValidationException, URISyntaxException {

    if (path.size() > MIN_DEPTH && String.join(".", path).length() > index.length()) {
      return null;
    }
    if (!refVal.startsWith("file:")) {
      return null;
    }
    refVal = URLDecoder.decode(refVal, "UTF-8").replace('\\', '/');

    String resourceName = null;
    String [] leads = { "/target/test-classes/", "/target/classes/" };
    for (String lead : leads) {
      int idx = refVal.indexOf(lead);
      if (idx != -1) {
        resourceName = refVal.substring(idx + lead.length());
        break;
      }
    }
    if (resourceName == null) {
      throw new IOException("$ref: Cannot find target/classes path in  " + refVal);
    }

    URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
    if (url == null) {
      throw new IOException("$ref: Cannot get resource for " + resourceName);
    }

    JsonFactory jsonFactory = new JsonFactory();
    JsonParser jp = jsonFactory.createParser(url);
    return recurseTop(index, iType, path, jp);
  }

  private Field recurseProperty(String index, String iType, Deque<String> path, JsonParser jp)
        throws IOException, SchemaException, QueryValidationException, URISyntaxException {

    Field field = null;
    String fieldName = jp.getCurrentName();
    path.addLast(fieldName);
    String type = null;
    while (!jp.isClosed()) {
      JsonToken jt = jp.nextToken();
      if (jt == null || jt.equals(JsonToken.END_OBJECT)) {
        break;
      }
      if (jt.equals(JsonToken.FIELD_NAME)) {
        switch (jp.getCurrentName()) {
          case PROPERTIES:
            field = recurseProperties(index, iType, path, jp);
            break;
          case TYPE:
            jp.nextToken();
            type = jp.getValueAsString();
            break;
          case ITEMS:
            field = recurseItems(index, iType, path, jp);
            break;
          case REF:
            jp.nextToken();
            field = recurseRef(index, iType, path, jp.getValueAsString());
            break;
          default:
          // ignore
        }
      }
    }
    field = matchLeaf(field, index, iType, path, type);
    path.removeLast();
    return field;
  }

  private Field recurseProperties(String index, String iType, Deque<String> path, JsonParser jp)
      throws IOException, SchemaException, QueryValidationException, URISyntaxException {
    Field field = null;
    while (!jp.isClosed()) {
      JsonToken jt = jp.nextToken();
      if (jt == null || jt.equals(JsonToken.END_OBJECT)) {
        break;
      }
      if (jt.equals(JsonToken.FIELD_NAME)) {
        Field f = recurseProperty(index, iType, path, jp);
        if (f != null) {
          field = f;
        }
      }
    }
    return field;
  }

  private Field recurseTop(String index, String iType, Deque<String> path, JsonParser jp)
      throws QueryValidationException, IOException, SchemaException, URISyntaxException {
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
   * Confirm that a particular field is valid for a given
   * schema. Returns a Field with type and field name.
   *
   * <p>The type of an array is string.
   *
   * @param index field name
   * @return type and path of the field.
   * @throws QueryValidationException if index does not exist in schema
   */
  public Field mapFieldNameAgainstSchema(String index) throws QueryValidationException {
    return mapFieldNameAndTypeAgainstSchema(index, null);
  }

  /**
   * Confirm that a particular field exists in the schema. If iType is not null
   * it also confirms that iType is the same as specified in the schema.
   *
   * <p>Currently, a type argument of
   * 'string' will match an index field of type 'string' or an array of any type.
   *
   * @param index field name
   * @param iType type of index
   * @return index
   * @throws QueryValidationException if index is not found in the schema
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
      return field;
    } catch (IOException | SchemaException | URISyntaxException e) {
      throw new QueryValidationException(e);
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
