package org.folio.cql2pgjson.model;

public class IndexTextAndJsonValues {
  public String indexText;
  public String indexJson;

  /**
   * the RAML type like integer, number, string, boolean, datetime, ... "" for
   * unknown.
   */
  public String type = "";
}
