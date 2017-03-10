package org.z3950.zing.cql.cql2pgjson;

public class ServerChoiceIndexesException extends CQL2PgJSONException {

  private static final long serialVersionUID = -7784937675579783786L;

  /**
  * serverChoiceConfig option is invalid
  */
  public ServerChoiceIndexesException( String message ) {
    super(message);
  }

}
