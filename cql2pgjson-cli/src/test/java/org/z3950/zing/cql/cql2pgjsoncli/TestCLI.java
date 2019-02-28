package org.z3950.zing.cql.cql2pgjsoncli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import org.apache.commons.cli.ParseException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.QueryValidationException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;

import static org.z3950.zing.cql.cql2pgjsoncli.CQL2PGCLIMain.readFile;

public class TestCLI {
  int exitStatus;
  String instanceSchemaPath;
  String holdingSchemaPath;
  String dbSchemaPath;

  @Before
  public void setup() throws URISyntaxException {
    exitStatus = 0;
    CQL2PGCLIMain.exit = status -> exitStatus = status;
    instanceSchemaPath = Paths.get(ClassLoader.getSystemResource("instance.json").toURI()).toString();
    holdingSchemaPath = Paths.get(ClassLoader.getSystemResource("holdingsrecord.json").toURI()).toString();
    dbSchemaPath = Paths.get(ClassLoader.getSystemResource("dbschema.json").toURI()).toString();
  }

  private void main(String arguments) {
    CQL2PGCLIMain.main(arguments.split(" "));
  }

  @Test
  public void testMainWithoutArguments() throws Exception {
    main("");
    assertEquals(1, exitStatus);
  }

  @Test
  public void testMain() throws Exception {
    main("-t instance cql.allRecords=1");
    assertEquals(0, exitStatus);
  }

  private String handleOptions(String arguments) throws Exception {
    return CQL2PGCLIMain.handleOptions(arguments.split(" "));
  }

  private String handleOptions(String[] arguments) throws Exception {
    return CQL2PGCLIMain.handleOptions(arguments);
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

  @Test
  public void testCLIFieldSchemaMap() throws Exception {
    String actualSql = handleOptions("-t item -m src/test/resources/fieldschemamap.json foobar=abc");
    assertEquals(
        "select * from item where lower(f_unaccent(item.jsonb->>'foobar')) ~ "
        + "lower(f_unaccent('(^|[[:punct:]]|[[:space:]]|(?=[[:punct:]]|[[:space:]]))abc($|[[:punct:]]|[[:space:]]|(?<=[[:punct:]]|[[:space:]]))'))",
        actualSql);
  }

  @Test
  public void testCLIFieldSchemaMapString() throws Exception {
    String actualSql = handleOptions("-t item -m {\"item.jsonb\":\"src/test/resources/item.json\"} foobar=abc");
    assertEquals(
        "select * from item where lower(f_unaccent(item.jsonb->>'foobar')) ~ "
        + "lower(f_unaccent('(^|[[:punct:]]|[[:space:]]|(?=[[:punct:]]|[[:space:]]))abc($|[[:punct:]]|[[:space:]]|(?<=[[:punct:]]|[[:space:]]))'))",
        actualSql);
  }

  void testCLI(String cql, String expectedSql) throws Exception {
      String[] args = new String[] { "-t", "instance", "-s", instanceSchemaPath, cql };
      String actualSql = handleOptions(args);
      assertEquals(expectedSql, actualSql);
  }

  @Test
  public void testCLIAllRecords() throws Exception {
    testCLI("cql.allRecords=1",
        "select * from instance where true");
  }

  @Test
  public void testCLIAllRecordsSorted() throws Exception {
    testCLI("cql.allRecords=1 sortBy title",
        "select * from instance where true order by lower(f_unaccent(instance.jsonb->>'title'))");
  }

  @Test
  public void testCLIName() throws Exception {
    testCLI("title=foo",
        "select * from instance where to_tsvector('simple', instance.jsonb->>'title') @@ to_tsquery('simple','foo')");
  }

  @Test(expected = QueryValidationException.class)
  public void testCLIParseException() throws Exception {
    testCLI("x=foo", null);
  }
}
