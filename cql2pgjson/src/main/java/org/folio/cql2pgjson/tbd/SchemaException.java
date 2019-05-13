package org.folio.cql2pgjson.tbd;

import org.folio.cql2pgjson.exception.CQL2PgJSONException;

public class SchemaException extends CQL2PgJSONException {

  private static final long serialVersionUID = -1408582584379339849L;

  /**
   * An error was found in processing and validating the JSON data structure document
   */
  public SchemaException( String message ) {
    super(message);
  }

}
