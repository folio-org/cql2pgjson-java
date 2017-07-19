package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

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

}
