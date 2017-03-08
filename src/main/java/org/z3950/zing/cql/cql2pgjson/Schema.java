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

public class Schema {

  /**
   * Run a schema validation on the specified JSON file, producing errors if the file
   * is either invalid JSON, or merely incorrectly structured for the purpose of defining
   * the JSON data storage structure.
   * @param schemaFile
   * @throws IOException
   * @throws SchemaException
   */
  public Schema (String schemaFile) throws IOException, SchemaException {
    JsonFactory jsonFactory = new JsonFactory();
    try ( JsonParser jp = jsonFactory.createParser(schemaFile) ) {
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
   * Confirm that a particular field is valid and unambiguous for a given schema. The return
   * value with be the fully-specified version of the (possibly abbreviated) field name. For example,
   * looking up 'zip' may return 'address.zip'.
   * @param index
   * @return fully-specified version of field value.
   * @throws QueryValidationException 
   */
  public String mapFieldNameAgainstSchema(String index) throws QueryValidationException {
    if (! _byNodeName.containsKey(index)) 
      throw new QueryValidationException( "Field name '"+index+"' not present in index." );
    JsonPath path = _byNodeName.get(index);
    if (path instanceof MultipleJsonPath) {
      throw new QueryAmbiguousExeption( "Field name '"+index+"' was ambiguous in index. ("+
          ((MultipleJsonPath) path).paths.stream().map(p->p.path).collect(Collectors.joining(", "))+")");
    }
    return path.path;
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
   * @throws QueryValidationException 
   */
  public String mapFieldNameAndTypeAgainstSchema(String index, String type) throws QueryValidationException {
    if (! _byNodeName.containsKey(index))
      throw new QueryValidationException( "Field name '"+index+"' not present in index." );
    JsonPath path = _byNodeName.get(index);
    if (path instanceof MultipleJsonPath) {
      List<JsonPath> matchingPaths = new ArrayList<>();
      for (JsonPath p : ((MultipleJsonPath)path).paths)
        if (type.equals(p.type) || type.equals(p.items))
          matchingPaths.add(p);
      if (matchingPaths.size() == 0)
        throw new QueryValidationException( "Field name '"+index+"' with type '"+type+"' not present in index." );
      else if (matchingPaths.size() == 1)
        return matchingPaths.get(0).path;
      throw new QueryAmbiguousExeption( "Field name '"+index+"' with type '"+type+"' was ambiguous in index. ("+
          matchingPaths.stream().map(p->p.path).collect(Collectors.joining(", "))+")");
    }
    if (type.equals(path.type) || type.equals(path.items))
        return path.path;
    throw new QueryValidationException( "Field name '"+index+"' with type '"+type+"' not present in index." );
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
            +items_usage);
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
    if (! _byNodeName.containsKey(nodeName)) {
      _byNodeName.put(nodeName, path);
    } else {
      JsonPath prevPath = _byNodeName.get(nodeName);
      if (prevPath instanceof MultipleJsonPath) {
        ((MultipleJsonPath) prevPath).paths.add(path);
      } else {
        MultipleJsonPath multiPath = new MultipleJsonPath();
        multiPath.paths.add(prevPath);
        multiPath.paths.add(path);
        _byNodeName.put(nodeName, multiPath);
      }
    }
  }

  private String getItems(JsonParser jp, List<String> breadcrumbs) throws IOException, SchemaException {
    JsonToken jt = jp.nextToken();
    String type = null;
    if (!jt.equals(JsonToken.START_OBJECT)) 
      throw new SchemaException(items_usage);
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
      throw new SchemaException(items_usage);
    return type;
  }

  private Map<String,JsonPath> _byNodeName = new HashMap<>();
  private final String items_usage =
      "`items` is a reserved field name, whose value should be a Json object containing `type` field.";

  private class JsonPath {
    String path = null;
    String type = null;
    String items = null;
  }
  private class MultipleJsonPath extends JsonPath {
    List<JsonPath> paths = new ArrayList<>();
  }
}
