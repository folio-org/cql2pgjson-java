package org.z3950.zing.cql.cql2pgjson;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

public class DBSchemaTest {

  @Test
  public void makeInstanceWithSpecifiedDBSchemaPath() throws Exception {
    Path dbSchemaPath = Paths.get(ClassLoader.getSystemResource("test_db_schema.json").toURI());
    if(dbSchemaPath == null) {
      throw new Exception("Can't find path");
    }
    CQL2PgJSON cql2pgjson = new CQL2PgJSON("instance.jsonb");
    cql2pgjson.setDbSchema(dbSchemaPath.toString());
    JSONObject dbSchema = cql2pgjson.getDbSchema();
    JSONArray tables = dbSchema.getJSONArray("tables");
    String[] tableArray = new String[]{ "loan_type", "material_type", "service_point_user" };
    for(String tableName : tableArray) {
      boolean found = false;
      for(Object ob : tables) {
        JSONObject jOb = (JSONObject)ob;
        if(jOb.getString("tableName").equals(tableName)) {
          found = true;
          break;
        }
      }
      if(!found) {
        throw new Exception(String.format("Missing tableName '%s' in db schema", tableName));
      }
    }
  }
}
