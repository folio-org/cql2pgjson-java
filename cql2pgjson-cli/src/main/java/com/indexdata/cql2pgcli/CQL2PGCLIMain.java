package com.indexdata.cql2pgcli;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.*;
import org.json.JSONException;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.QueryValidationException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;
import org.z3950.zing.cql.cql2pgjson.SqlSelect;
import org.json.JSONObject;

public class CQL2PGCLIMain { 
  
  public static void main( String[] args ) {
    Options options = new Options();
    
    Option database = Option.builder("d")
        .hasArg()
        .required(true)
        .desc("Postgres database name")
        .build();
    
    Option field = Option.builder("f")
        .hasArg()
        .required(true)
        .desc("Postgres field name")
        .build();
    
    Option schema = Option.builder("s")
        .hasArg()
        .desc("Path to JSON schema file")
        .build();
    
    Option dbschema = Option.builder("m")
        .hasArg()
        .desc("A JSON string or a pathname to a JSON file to describe the db/schema map")
        .build();
    
    options.addOption(schema);
    options.addOption(database);
    options.addOption(field);
    options.addOption(dbschema);
    CommandLineParser parser = new DefaultParser();
    CQL2PgJSON cql2pgJson = null;
    try {
      CommandLine line = parser.parse(options, args);
      String fullDbName = line.getOptionValue("d") + "." + line.getOptionValue("f");
      if(!line.hasOption("m")) {
        if(line.hasOption("s")) {       
          String schemaText = readFile(line.getOptionValue("s"), Charset.forName("UTF-8"));   
          cql2pgJson = new CQL2PgJSON(fullDbName, schemaText);
        } else {
          cql2pgJson = new CQL2PgJSON(fullDbName); //No schemas
        }
      } else {
        Map<String, String> fieldSchemaMap = parseDatabaseSchemaString(line.getOptionValue("m"));
        cql2pgJson = new CQL2PgJSON(fieldSchemaMap);        
      }
      List<String> cliArgs = line.getArgList();  
      String cql = cliArgs.get(0);
      System.out.println(parseCQL(cql2pgJson, line.getOptionValue("d"), cql));
    } catch( Exception e ) {
      System.err.println(String.format("Got error %s, %s: ", e.getClass().toString(), e.getLocalizedMessage()));
      e.printStackTrace();
      System.exit(1);
    }
    
  }
  
  static String readFile(String path, Charset encoding) throws IOException 
  {
    System.out.println("Reading file " + path);
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    String content = new String(encoded, encoding);
    //System.out.println(String.format("Content of %s is: %s", path, content));
    return content;
  }
  
  static String parseCQL(CQL2PgJSON cql2pgJson, String dbName, String cql) throws IOException,
      FieldException, SchemaException, QueryValidationException {
    SqlSelect sql = cql2pgJson.toSql(cql);
    return String.format("select * from %s where %s order by %s",
        dbName, sql.getWhere(), sql.getOrderBy());
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
      String fieldSchemaJsonText = readFile(dbsString, Charset.forName("UTF-8"));
      fieldSchemaJson = new JSONObject(fieldSchemaJsonText);     
    }
    if(fieldSchemaJson == null) {
      throw new IOException(String.format("Unable to get valid JSON from string or file path for %s",
          dbsString));
    }
    for(String key : fieldSchemaJson.keySet()) {
      String value = readFile(fieldSchemaJson.getString(key), Charset.forName("UTF-8"));
      fieldSchemaMap.put(key, value);
    }
    return fieldSchemaMap;
  }
  
}
