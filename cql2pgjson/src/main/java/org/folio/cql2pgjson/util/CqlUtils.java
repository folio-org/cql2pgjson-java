package org.folio.cql2pgjson.util;

/**
 * Help methods related to CQL syntax.
 *
 * @author hji
 *
 */
public class CqlUtils {

  /**
   * Get field name from CQL field like [schema.table.]field
   *
   * @param cqlField
   * @return
   */
  public static String getFieldNameFromCqlField(String cqlField) {
    String fieldName = cqlField.trim();
    if (!fieldName.contains(".")) {
      return fieldName;
    } else {
      return fieldName.substring(fieldName.lastIndexOf(".") + 1);
    }
  }

  /**
   * Get table name from CQL Field: [schema.table.]field
   *
   * @param cqlField
   * @return
   */
  public static String getTableNameFromCqlField(String cqlField) {
    String tableName = cqlField.trim();
    if (!tableName.contains(".")) {
      return null;
    }
    tableName = tableName.substring(0, tableName.lastIndexOf("."));
    if (tableName.contains(".")) {
      return tableName.substring(tableName.lastIndexOf(".") + 1);
    } else {
      return tableName;
    }
  }

  /**
   * Retrieve the field name from indexJson.
   *
   * @param indexJson
   * @return
   */
  public static String getFieldNameFromIndexJson(String indexJson) {
    return indexJson.substring(0, indexJson.indexOf("->"));
  }

  /**
   * Retrieve index name from indexJson.
   *
   * @param indexJson
   * @return
   */
  public static String getIndexNameFromIndexJson(String indexJson) {
    return indexJson.substring(indexJson.indexOf("->") + 3, indexJson.length() - 1).replace("'->'", ".");
  }

}
