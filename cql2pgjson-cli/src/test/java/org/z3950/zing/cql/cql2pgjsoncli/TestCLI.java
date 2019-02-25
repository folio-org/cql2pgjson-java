package org.z3950.zing.cql.cql2pgjsoncli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.cli.ParseException;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.QueryValidationException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;
import org.z3950.zing.cql.cql2pgjson.SqlSelect;
import static org.z3950.zing.cql.cql2pgjsoncli.CQL2PGCLIMain.readFile;

public class TestCLI {
  
  String instanceSchemaPath;
  String holdingSchemaPath;
  String dbSchemaPath;
  
  @Before
  public void setupClass() throws URISyntaxException {
    instanceSchemaPath = Paths.get(ClassLoader.getSystemResource("instance.json").toURI()).toString();
    holdingSchemaPath = Paths.get(ClassLoader.getSystemResource("holdingsrecord.json").toURI()).toString();
    dbSchemaPath = Paths.get(ClassLoader.getSystemResource("dbschema.json").toURI()).toString();
  }
  
  @Test
  public void testCLIWithNoSchemaOrDBSchema() throws FieldException, IOException,
      SchemaException, QueryValidationException, ParseException {
    String cql = "holdingsRecords.permanentLocationId=\"fcd64ce1-6995-48f0-840e-89ffa2\"";
    String[] args = new String[] {"-t", "instance", "-f", "jsonb", cql };
    String fullFieldName = "instance.jsonb";
    CQL2PgJSON cql2pgjson = new CQL2PgJSON(fullFieldName);
    String output = CQL2PGCLIMain.parseCQL(cql2pgjson, "instance", cql);
    String cli_output = CQL2PGCLIMain.handleOptions(args);
    assertNotNull(output);
    assertEquals(output, cli_output);
  }
  
  @Test
  public void testCLIWithSchema() throws IOException, FieldException, SchemaException,
      QueryValidationException, ParseException {
    String cql = "hrid=\"fcd64ce1-6995-48f0-840e-89ffa2\"";
    String[] args = new String[] {"-t", "instance", "-f", "jsonb", "-s", instanceSchemaPath, cql };
    String fullFieldName = "instance.jsonb";
    CQL2PgJSON cql2pgjson = new CQL2PgJSON(fullFieldName, readFile(instanceSchemaPath, Charset.forName("UTF-8")));
    String output = CQL2PGCLIMain.parseCQL(cql2pgjson, "instance", cql);
    String cli_output = CQL2PGCLIMain.handleOptions(args);
    assertNotNull(output);
    assertEquals(output, cli_output);
  }
  
  @Test
  public void testCLIWithDBSchema() throws FieldException, IOException, SchemaException,
      ParseException, QueryValidationException {
    String cql = "hrid=\"fcd64ce1-6995-48f0-840e-89ffa2\"";
    String[] args = new String[] {"-t", "instance", "-f", "jsonb", "-b", dbSchemaPath, cql };
    String fullFieldName = "instance.jsonb";
    CQL2PgJSON cql2pgjson = new CQL2PgJSON(fullFieldName, null, dbSchemaPath);
    String output = CQL2PGCLIMain.parseCQL(cql2pgjson, "instance", cql);
    String cli_output = CQL2PGCLIMain.handleOptions(args);
    assertNotNull(output);
    assertEquals(output, cli_output);
  }
}
