package org.z3950.zing.cql.cql2pgjson;

public class CQL2PgJSONException extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  /**
   * An error was found in CQL2PgJSONException
   */
  public CQL2PgJSONException( String message ) {
    super(message);
  }
  public CQL2PgJSONException( Exception e ) {
    super(e);
  }
}
