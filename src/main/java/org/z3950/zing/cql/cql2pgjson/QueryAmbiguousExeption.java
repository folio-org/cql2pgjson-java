package org.z3950.zing.cql.cql2pgjson;

public class QueryAmbiguousExeption extends QueryValidationException {

  /**
  * The CQL query provided identifies search fields ambiguously.
  */
  public QueryAmbiguousExeption( String message ) {
    super(message);
  }
}
