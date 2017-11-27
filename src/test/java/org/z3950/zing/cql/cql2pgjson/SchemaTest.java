package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class SchemaTest {

  @Test
  @Parameters({
    "city,         address.city",
    "address.city, address.city",
    "zip,          address.zip",
    "address.zip,  address.zip",
    "name,         name",
  })
  public void validFieldLookupsTest(String field, String fullField) throws Exception {
    Schema s = new Schema(Util.getResource("userdata.json"));
    assertThat(s.mapFieldNameAgainstSchema(field).getPath(), is(fullField));
  }

  /* Both `type` and `properties` are structural keywords, but can still be used for attributes. */
  @Test
  @Parameters({
    "type,           child.pet.type",
    "pet.type,       child.pet.type",
    "child.pet.type, child.pet.type",
    "properties,     parent.properties",
    "favoriteColor,  child.favoriteColor",
  })
  public void noReservedWordsTest(String field, String fullField) throws Exception {
    Schema s = new Schema(Util.getResource("complex.json"));
    assertThat(s.mapFieldNameAgainstSchema(field).getPath(), is(fullField));
  }

  @Test
  @Parameters({
    "userdata.json, city,         address.city",
    "userdata.json, address.city, address.city",
    "userdata.json, name,         name",
    "complex.json,  size,         parent.shirt.size",  // other size attributes are integers
  })
  public void validFieldsWithTypeSpeficiedTest(String schema, String field, String fullField) throws Exception {
    Schema s = new Schema(Util.getResource(schema));
    assertThat(s.mapFieldNameAndTypeAgainstSchema(field, "string").getPath(), is(fullField));
  }

  @Test(expected=QueryValidationException.class)
  public void invalidFieldLookupTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    s.mapFieldNameAgainstSchema("fakeField");
  }

  @Test(expected=QueryValidationException.class)
  public void inValidFieldsWithTypeSpeficiedTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    s.mapFieldNameAndTypeAgainstSchema("city","integer");
  }

  @Test(expected=QueryAmbiguousException.class)
  public void ambiguousQueryTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("complex.json") );
    s.mapFieldNameAgainstSchema("size");
  }
  @Test(expected=QueryAmbiguousException.class)
  public void ambiguousQueryTestWithType() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("complex.json") );
    s.mapFieldNameAndTypeAgainstSchema("size","integer");
  }

  @Test
  public void ambiguousQueryMessageTest() throws IOException, SchemaException, QueryValidationException {
    Schema s = new Schema( Util.getResource("complex.json") );
    try {
      s.mapFieldNameAgainstSchema("color");
      fail("QueryAmbiguousExeption not thrown");
    } catch (QueryAmbiguousException e) {
      assertEquals(e.getMessage(),
          "Field name 'color' was ambiguous in index. "
          + "(parent.shirt.color, parent.pants.color, parent.shoes.color, child.pet.color)");
    }
  }

  @Test(expected = SchemaException.class)
  public void objectWithoutType() throws SchemaException, IOException {
    new Schema("{ \"title\": \"T\", \"type\": \"object\", \"properties\": { "
        + "\"foo\": { \"type\": \"array\", \"items\" : {} } } }");
  }

  @Test(expected = SchemaException.class)
  public void arrayWithoutContent() throws SchemaException, IOException {
    new Schema("{ \"title\": \"T\", \"type\": \"object\", \"properties\": { "
        + "\"foo\": { \"type\": \"array\" } }");
  }
}
