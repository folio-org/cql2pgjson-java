package org.z3950.zing.cql.cql2pgjsoncli;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import org.apache.commons.cli.*;
import org.json.JSONException;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.QueryValidationException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;
import org.z3950.zing.cql.cql2pgjson.SqlSelect;
import org.json.JSONObject;

public class CQL2PGCLIMain {

  /** allow to inject a different exit method for unit testing */
  static IntConsumer exit = System::exit;

  public static void main( String[] args ) {
    try {
      System.out.println(handleOptions(args));
    } catch( Exception e ) {
      System.err.println(String.format("Got error %s, %s: ", e.getClass().toString(),
          e.getLocalizedMessage()));
      e.printStackTrace();
      exit.accept(1);
    }
  }

  static String handleOptions(String[] args) throws
      FieldException, SchemaException, IOException, QueryValidationException,
      ParseException {
    Options options = new Options();

    Option database = Option.builder("t")
        .hasArg()
        .required(true)
        .desc("Postgres table name")
        .build();

    Option field = Option.builder("f")
        .hasArg()
        .required(false)
        .desc("Postgres field name")
        .build();

    Option schema = Option.builder("s")
        .hasArg()
        .desc("Path to JSON schema file")
        .build();

    Option schemamap = Option.builder("m")
        .hasArg()
        .desc("A JSON string or a pathname to a JSON file to describe the db/schema map")
        .build();

    Option dbschema = Option.builder("b")
        .hasArg()
        .desc("Path to RMB-style schema.json to describe database")
        .build();

    options.addOption(schema);
    options.addOption(database);
    options.addOption(field);
    options.addOption(schemamap);
    options.addOption(dbschema);

    CommandLineParser parser = new DefaultParser();
    CommandLine line = parser.parse(options, args);
    CQL2PgJSON cql2pgJson = null;
    String fullFieldName = line.getOptionValue("t") + "." + line.getOptionValue("f", "jsonb");
    if(!line.hasOption("m")) {
      if(line.hasOption("b")) {
        cql2pgJson = new CQL2PgJSON(fullFieldName, null, line.getOptionValue("b"));
      } else if(line.hasOption("s")) {       
        String schemaText = readFile(line.getOptionValue("s"), StandardCharsets.UTF_8);
        cql2pgJson = new CQL2PgJSON(fullFieldName, schemaText);
      } else {
        cql2pgJson = new CQL2PgJSON(fullFieldName); //No schemas
      }
    } else {
      Map<String, String> fieldSchemaMap = parseDatabaseSchemaString(line.getOptionValue("m"));
      cql2pgJson = new CQL2PgJSON(fieldSchemaMap);
    }
    List<String> cliArgs = line.getArgList();
    String cql = cliArgs.get(0);
    return parseCQL(cql2pgJson, line.getOptionValue("t"), cql);
  }

  static String readFile(String path, Charset encoding) throws IOException
  {
    System.out.println("Reading file " + path);
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    String content = new String(encoded, encoding);
    //System.out.println(String.format("Content of %s is: %s", path, content));
    return content;
  }

  static protected String parseCQL(CQL2PgJSON cql2pgJson, String dbName, String cql) throws IOException,
      FieldException, SchemaException, QueryValidationException {
    SqlSelect sql = cql2pgJson.toSql(cql);
    String orderby = sql.getOrderBy();
    if(orderby != null && orderby.length() > 0) {
      return String.format("select * from %s where %s order by %s",
          dbName, sql.getWhere(), orderby);
    } else {
       return String.format("select * from %s where %s",
          dbName, sql.getWhere());
    }
  }

  /*
    If the string is valid JSON, read the values from the JSON object. If the
    string is a path to a JSON file, load the file and read the JSON from the
    file
  */
  static Map<String, String> parseDatabaseSchemaString(String dbsString) throws
      IOException {
    JSONObject fieldSchemaJson = null;
    Map<String, String> fieldSchemaMap = new HashMap<>();
    try {
      fieldSchemaJson = new JSONObject(dbsString);
    } catch( JSONException je ) {
      System.out.println(String.format("Unable to parse %s as JSON: %s",
          dbsString, je.getLocalizedMessage()));
    }
    if(fieldSchemaJson == null) {
      String fieldSchemaJsonText = readFile(dbsString, StandardCharsets.UTF_8);
      fieldSchemaJson = new JSONObject(fieldSchemaJsonText);     
    }
    for(String key : fieldSchemaJson.keySet()) {
      String value = readFile(fieldSchemaJson.getString(key), StandardCharsets.UTF_8);
      fieldSchemaMap.put(key, value);
    }
    return fieldSchemaMap;
  }

}
