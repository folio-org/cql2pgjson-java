package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Resolves index names against a JSON schema. Can return the fully qualified index
 * and the cqlRelation for it.
 */
public class Schema {

  /* Private use variables and object structures*/
  private final Map<String,JsonPath> byNodeName = new HashMap<>();
  private static final String ITEMS_USAGE_MESSAGE =
      "`items` is a reserved field name, whose value should be a Json object containing `type` field.";

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
     * @return full path
     */
    String getPath() {
      return path;
    }

    /**
     * RAML type like integer, number, string, boolean, datetime, ...
     * "" for unknown.
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

  private static class MultipleJsonPath extends JsonPath {
    List<JsonPath> paths = new ArrayList<>();
  }
  /* End of private use variables and object structures*/

  /**
   * Run a schema validation on the specified JSON file, producing errors if the file
   * is either invalid JSON, or merely incorrectly structured for the purpose of defining
   * the JSON data storage structure.
   * @param schemaJson
   * @throws IOException
   * @throws SchemaException
   */
  public Schema (String schemaJson) throws IOException, SchemaException {
    JsonFactory jsonFactory = new JsonFactory();
    try ( JsonParser jp = jsonFactory.createParser(schemaJson) ) {
      while (!jp.isClosed()) {
        JsonToken jt = jp.nextToken();
        if (jt == null) break;
        if (jt.equals(JsonToken.FIELD_NAME))
          if (jp.getCurrentName().equals("properties"))
            iteratePropertiesArray(jp,new ArrayList<>());
      }
    }
  }

  /**
   * Confirm that a particular field is valid and unambiguous for a given schema. Returns a Field
   * with type and the fully-specified version of the (possibly abbreviated) field name. For example,
   * looking up 'zip' may return 'address.zip' as path.
   * @param index  field name, may be abbreviated
   * @return type and path of the field.
   * @throws QueryValidationException on ambiguous index
   */
  public Field mapFieldNameAgainstSchema(String index) throws QueryValidationException {
    JsonPath path = getPath(index);
    if (path instanceof MultipleJsonPath) {
      throw queryAmbiguousException(index, null, ((MultipleJsonPath) path).paths);
    }
    return new Field(path.path, path.type);
  }

  /**
   * Confirm that a particular field is valid and unambiguous for a given schema. The return
   * value with be the fully-specified version of the (possibly abbreviated) field name. For example,
   * looking up 'zip' may return 'address.zip'. Any fields in the index that don't match the specified
   * type will not be considered to be matches. Currently, a type argument of 'string' will match an
   * index field of type 'string' or an array of type 'string'.
   * @param index
   * @param type
   * @return fully-specified version of field value.
   * @throws QueryValidationException  if index and type is not found or ambiguous
   */
  public Field mapFieldNameAndTypeAgainstSchema(String index, String type) throws QueryValidationException {
    JsonPath path = getPath(index);
    if (path instanceof MultipleJsonPath) {
      List<JsonPath> matchingPaths = new ArrayList<>();
      for (JsonPath p : ((MultipleJsonPath)path).paths) {
        if (type.equals(p.type) || type.equals(p.items)) {
          matchingPaths.add(p);
        }
      }
      if (matchingPaths.isEmpty()) {
        throw queryValidationException(index, type);
      }
      if (matchingPaths.size() > 1) {
        throw queryAmbiguousException(index, type, matchingPaths);
      }
      path = matchingPaths.get(0);
    }
    if (! type.equals(path.type) && ! type.equals(path.items)) {
      throw queryValidationException(index, type);
    }
    return new Field(path.path, path.type);
  }

  /**
   * Get JsonPath for index.
   * @param index the index to search for
   * @return the path
   * @throws QueryValidationException  if index is not present.
   */
  private JsonPath getPath(String index) throws QueryValidationException {
    if (! byNodeName.containsKey(index)) {
      throw queryValidationException(index, null);
    }
    return byNodeName.get(index);
  }

  /**
   * Create a QueryAmbiguousException with message naming index, type and paths.
   * @param index  index
   * @param type  type (may be null)
   * @param paths  the paths that match index and type
   * @return the exception
   */
  private QueryAmbiguousException queryAmbiguousException(String index, String type, List<JsonPath> paths) {
    return new QueryAmbiguousException(
        "Field name '" + index
        + (type == null ? "" : "' with type '" + type)
        + "' was ambiguous in index. ("
        + paths.stream().map(p->p.path).collect(Collectors.joining(", ")) + ")");
  }

  /**
   * Create a QueryValidationExceptionn for index and type that are not found.
   * @param index  index
   * @param type  type (may be null)
   * @return the exception
   */
  private QueryValidationException queryValidationException(String index, String type) {
    return new QueryValidationException(
        "Field name '" + index
        + (type == null ? "" : "' with type '" + type)
        + "' is not present in index.");
  }

  /* Private methods involved with Schema validation */
  private void iteratePropertiesArray(JsonParser jp, List<String> breadcrumbs) throws IOException, SchemaException {
    while (!jp.isClosed()) {
      JsonToken jt = jp.nextToken();
      if (jt.equals(JsonToken.FIELD_NAME))
        processNode(jp,breadcrumbs);
      else if (jt.equals(JsonToken.END_OBJECT))
        return;
    }
  }

  private void processNode(JsonParser jp, List<String> breadcrumbs) throws IOException, SchemaException {
    String fieldName = jp.getCurrentName();
    JsonToken jt = null;
    String type = null;
    String items = null;
    while (!jp.isClosed()) {
      jt = jp.nextToken();
      if (jt.equals(JsonToken.FIELD_NAME)) {
        String subFieldName = jp.getCurrentName();
        if (subFieldName.equals("properties")) {
          breadcrumbs.add(fieldName);
          iteratePropertiesArray(jp,breadcrumbs);
          breadcrumbs.remove(breadcrumbs.size()-1);
        } else if (subFieldName.equals("type")) {
          jp.nextToken();
          type = jp.getValueAsString();
        } else if (subFieldName.equals("items")) {
          breadcrumbs.add(fieldName);
          items = getItems( jp, breadcrumbs );
          breadcrumbs.remove(breadcrumbs.size()-1);
        }
      } else if (jt.equals(JsonToken.END_OBJECT)) {
        recordFoundNode(type,items,fieldName,breadcrumbs);
        return;
      }
    }
  }

  private void recordFoundNode(String type, String items, String fieldName, List<String> breadcrumbs) throws SchemaException {
    if (type == null)
      return;
    if (type.equals("array") && items == null)
        throw new SchemaException("Array type nodes require an items object to identify the object type in the array."
            +ITEMS_USAGE_MESSAGE);
    breadcrumbs.add(fieldName);
    JsonPath path = new JsonPath();
    path.path = String.join(".", breadcrumbs);
    path.type = type;
    path.items = items;
    int breadcrumbsSize = breadcrumbs.size();
    for (int i = breadcrumbsSize-1; i >= 0; i--) {
      saveToNodeNameMap( path, String.join(".",breadcrumbs.subList(i, breadcrumbsSize)) );
    }
    breadcrumbs.remove(breadcrumbsSize-1);
  }

  private void saveToNodeNameMap(JsonPath path, String nodeName) {
    if (! byNodeName.containsKey(nodeName)) {
      byNodeName.put(nodeName, path);
    } else {
      JsonPath prevPath = byNodeName.get(nodeName);
      if (prevPath instanceof MultipleJsonPath) {
        ((MultipleJsonPath) prevPath).paths.add(path);
      } else {
        MultipleJsonPath multiPath = new MultipleJsonPath();
        multiPath.paths.add(prevPath);
        multiPath.paths.add(path);
        byNodeName.put(nodeName, multiPath);
      }
    }
  }

  private String getItems(JsonParser jp, List<String> breadcrumbs) throws IOException, SchemaException {
    JsonToken jt = jp.nextToken();
    String type = null;
    if (!jt.equals(JsonToken.START_OBJECT))
      throw new SchemaException(ITEMS_USAGE_MESSAGE);
    while ( ! jp.isClosed() && ! jt.equals(JsonToken.END_OBJECT)) {
      jt = jp.nextToken();
      if (jt.equals(JsonToken.FIELD_NAME)) {
        if (jp.getCurrentName().equals("type")) {
          jp.nextValue();
          type = jp.getValueAsString();
        } else if (jp.getCurrentName().equals("properties")) {
          iteratePropertiesArray(jp,breadcrumbs);
        }
      }
    }
    if (type == null)
      throw new SchemaException(ITEMS_USAGE_MESSAGE);
    return type;
  }

}
