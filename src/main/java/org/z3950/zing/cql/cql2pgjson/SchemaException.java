package org.z3950.zing.cql.cql2pgjson;

public class SchemaException extends Exception {

  /**
   * An error was found in processing and validating the JSON data structure document
   */
  public SchemaException( String message ) {
    super(message);
  }

}
