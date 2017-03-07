package org.z3950.zing.cql.cql2pgjson;

public class QueryValidationException extends Exception {
 
  /**
  * The CQL query provided does not appear to be valid.
  */
  public QueryValidationException( String message ) {
    super(message);
  }
}
