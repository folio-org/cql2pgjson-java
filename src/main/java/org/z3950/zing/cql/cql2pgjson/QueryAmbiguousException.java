package org.z3950.zing.cql.cql2pgjson;

public class QueryAmbiguousException extends QueryValidationException {

  /**
   *
   */
  private static final long serialVersionUID = 3243493577758621407L;

  /**
  * The CQL query provided identifies search fields ambiguously.
  */
  public QueryAmbiguousException( String message ) {
    super(message);
  }
}
