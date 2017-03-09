package org.z3950.zing.cql.cql2pgjson;

public class QueryValidationException extends CQL2PgJSONException {
 
  /**
   * 
   */
  private static final long serialVersionUID = 2171623606570086123L;

  /**
  * The CQL query provided does not appear to be valid.
  */
  public QueryValidationException( String message ) {
    super(message);
  }
  public QueryValidationException( Exception e ) {
    super(e);
  }
}
