package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;

import org.junit.Test;

public class MultiFieldProcessingTest {

  @Test(expected = FieldException.class)
  public void testBadFieldName1() throws FieldException {
    new CQL2PgJSON( Arrays.asList("usersbl.json",""));
  }

  @Test(expected = FieldException.class)
  public void testBadFieldName2() throws FieldException {
    new CQL2PgJSON( Arrays.asList(null,"jsonb"));
  }

  @Test
  public void testApplicationOfFieldNamesWithoutSchema() throws FieldException, QueryValidationException {
    CQL2PgJSON converter = new CQL2PgJSON( Arrays.asList("field1","field2") );
    assertEquals(
        "field1->>'name' ~ '(^|[[:punct:]]|[[:space:]])[VvᵛᵥṼṽṾṿⅤⅴⓋⓥⱽＶｖ]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("field1.name=v"));
    assertEquals(
        "field2->>'name' ~ '(^|[[:punct:]]|[[:space:]])[vᵛᵥṽṿⅴⓥｖ]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("field2.name=/respectCase v"));
    assertEquals(
        "field1->>'name' ~ '(^|[[:punct:]]|[[:space:]])[Vv]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("name=/respectAccents v"));
  }

  @Test
  public void testApplicationOfFieldNamesWithSchema()
      throws FieldException, QueryValidationException, SchemaException, IOException {
    LinkedHashMap<String,String> fieldsAndSchemas = new LinkedHashMap<>();
    fieldsAndSchemas.put("field1", Util.getResource("userdata.json"));
    fieldsAndSchemas.put("field2", Util.getResource("userdata.json"));
    CQL2PgJSON converter = new CQL2PgJSON( fieldsAndSchemas );
    assertEquals(
        "field1->>'name' ~ '(^|[[:punct:]]|[[:space:]])[VvᵛᵥṼṽṾṿⅤⅴⓋⓥⱽＶｖ]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("field1.name=v"));
    assertEquals(
        "field2->>'name' ~ '(^|[[:punct:]]|[[:space:]])[vᵛᵥṽṿⅴⓥｖ]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("field2.name=/respectCase v"));
    assertEquals(
        "field1->>'name' ~ '(^|[[:punct:]]|[[:space:]])[Vv]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("name=/respectAccents v"));
  }

  @Test
  public void testApplicationOfFieldNamesWithServerChoiceIndexes()
      throws FieldException, QueryValidationException, ServerChoiceIndexesException {
    CQL2PgJSON converter = new CQL2PgJSON( Arrays.asList("field1","field2") );
    converter.setServerChoiceIndexes(Arrays.asList("field1.name"));
    assertEquals(
        "field1->>'name' ~ '(^|[[:punct:]]|[[:space:]])[VvᵛᵥṼṽṾṿⅤⅴⓋⓥⱽＶｖ]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("v"));
    converter.setServerChoiceIndexes(Arrays.asList("field2.name"));
    assertEquals(
        "field2->>'name' ~ '(^|[[:punct:]]|[[:space:]])[VvᵛᵥṼṽṾṿⅤⅴⓋⓥⱽＶｖ]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("v"));
    converter.setServerChoiceIndexes(Arrays.asList("name"));
    assertEquals(
        "field1->>'name' ~ '(^|[[:punct:]]|[[:space:]])[VvᵛᵥṼṽṾṿⅤⅴⓋⓥⱽＶｖ]($|[[:punct:]]|[[:space:]])'",
        converter.cql2pgJson("v"));
  }

  @Test(expected = QueryValidationException.class)
  public void testSchemaValidationInMultiFieldQuery()
      throws FieldException, SchemaException, IOException, QueryValidationException {
    LinkedHashMap<String,String> fieldsAndSchemas = new LinkedHashMap<>();
    fieldsAndSchemas.put("field1", Util.getResource("userdata.json"));
    fieldsAndSchemas.put("field2", Util.getResource("userdata.json"));
    CQL2PgJSON converter = new CQL2PgJSON( fieldsAndSchemas );
    converter.cql2pgJson("field2.notInSchema=/respectCase v");
  }

  @Test
  public void testMixedFieldQuery() throws FieldException, QueryValidationException {
    CQL2PgJSON converter = new CQL2PgJSON( Arrays.asList("field1","field2") );
    String expected =
        "(field1->>'name' ~ '(^|[[:punct:]]|[[:space:]])Smith($|[[:punct:]]|[[:space:]])')"
        + " AND (field1->>'email' ~ '(^|[[:punct:]]|[[:space:]])[Gg][Mm][Aa][Iiı][Ll]\\.[Cc][Oo][Mm]($|[[:punct:]]|[[:space:]])')"
        + " ORDER BY lower(f_unaccent(field2->>'name'))";
    assertEquals(expected,
        converter.cql2pgJson(
            "name =/respectCase/respectAccents Smith"
                + " AND email =/respectAccents gmail.com"
                + " sortBy field2.name/sort.ascending"));
  }

}
