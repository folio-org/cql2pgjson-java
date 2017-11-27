package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

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
    assertThat(converter.cql2pgJson("field1.name=v"),
        containsString("lower(f_unaccent(field1->>'name')) ~ lower(f_unaccent('"));
    assertThat(converter.cql2pgJson("field2.name=/respectCase v"),
        containsString("f_unaccent(field2->>'name') ~ f_unaccent('"));
    assertThat(converter.cql2pgJson("name=/respectAccents v"),
        containsString("lower(field1->>'name') ~ lower('"));

  }

  @Test
  public void testApplicationOfFieldNamesWithSchema()
      throws FieldException, QueryValidationException, SchemaException, IOException {
    LinkedHashMap<String,String> fieldsAndSchemas = new LinkedHashMap<>();
    fieldsAndSchemas.put("field1", Util.getResource("userdata.json"));
    fieldsAndSchemas.put("field2", Util.getResource("userdata.json"));
    CQL2PgJSON converter = new CQL2PgJSON( fieldsAndSchemas );
    assertThat(converter.cql2pgJson("field1.name=v"),
        containsString("lower(f_unaccent(field1->>'name')) ~ lower(f_unaccent('"));
    assertThat(converter.cql2pgJson("field2.name=/respectCase v"),
        containsString("f_unaccent(field2->>'name') ~ f_unaccent('"));
    assertThat(converter.cql2pgJson("name=/respectAccents v"),
        containsString("lower(field1->>'name') ~ lower('"));
  }

  @Test
  public void testApplicationOfFieldNamesWithServerChoiceIndexes()
      throws FieldException, QueryValidationException, ServerChoiceIndexesException {
    CQL2PgJSON converter = new CQL2PgJSON( Arrays.asList("field1","field2") );
    converter.setServerChoiceIndexes(Arrays.asList("field1.name"));
    assertThat(converter.cql2pgJson("v"),
        containsString("lower(f_unaccent(field1->>'name')) ~ lower(f_unaccent('"));
    converter.setServerChoiceIndexes(Arrays.asList("field2.name"));
    assertThat(converter.cql2pgJson("v"),
        containsString("lower(f_unaccent(field2->>'name')) ~ lower(f_unaccent('"));
    converter.setServerChoiceIndexes(Arrays.asList("name"));
    assertThat(converter.cql2pgJson("v"),
        containsString("lower(f_unaccent(field1->>'name')) ~ lower(f_unaccent('"));
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
    assertThat(converter.cql2pgJson(
            "name =/respectCase/respectAccents Smith"
                + " AND email =/respectAccents gmail.com"
                + " sortBy field2.name/sort.ascending"),
        allOf(containsString("field1->>'name' ~ '"),
              containsString("lower(field1->>'email') ~ lower('"),
              containsString("ORDER BY lower(f_unaccent(field2->>'name'))")));
  }

}
