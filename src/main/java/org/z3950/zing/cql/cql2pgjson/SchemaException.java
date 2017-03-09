package org.z3950.zing.cql.cql2pgjson;

public class SchemaException extends CQL2PgJSONException {

  private static final long serialVersionUID = -1408582584379339849L;

  /**
   * An error was found in processing and validating the JSON data structure document
   */
  public SchemaException( String message ) {
    super(message);
  }

}
