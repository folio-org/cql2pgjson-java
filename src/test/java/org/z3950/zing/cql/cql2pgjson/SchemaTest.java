package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class SchemaTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private void expect(Class<? extends Throwable> type) {
    thrown.expect(type);
  }

  private void expect(Class<? extends Throwable> type, String substring) {
    thrown.expect(type);
    thrown.expectMessage(containsString(substring));
  }

  private void expect(Class<? extends Throwable> type, String substring1, String substring2, String substring3) {
    thrown.expect(type);
    thrown.expectMessage(allOf(containsString(substring1), containsString(substring2), containsString(substring3)));
  }

  @Test
  public void emptySchema() throws Exception {
    new Schema("{}");
  }

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

  @Test
  public void fieldsWithTypeNotFound() throws Exception {
    Schema s = new Schema(Util.getResource("complex.json"));
    expect(QueryValidationException.class, "Field name", "size", "is not present");
    s.mapFieldNameAndTypeAgainstSchema("size", "date");
  }

  @Test
  public void invalidFieldLookupTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    expect(QueryValidationException.class, "Field name", "fakeField", "is not present");
    s.mapFieldNameAgainstSchema("fakeField");
  }

  @Test
  public void inValidFieldsWithTypeSpeficiedTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    expect(QueryValidationException.class, "Field name", "city", "is not present");
    s.mapFieldNameAndTypeAgainstSchema("city","integer");
  }

  @Test
  public void ambiguousQueryTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("complex.json") );
    expect(QueryAmbiguousException.class);
    s.mapFieldNameAgainstSchema("size");
  }
  @Test
  public void ambiguousQueryTestWithType() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("complex.json") );
    expect(QueryAmbiguousException.class);
    s.mapFieldNameAndTypeAgainstSchema("size","integer");
  }

  @Test
  public void ambiguousQueryMessageTest() throws IOException, SchemaException, QueryValidationException {
    Schema s = new Schema( Util.getResource("complex.json") );
    expect(QueryAmbiguousException.class, "Field name 'color' was ambiguous in index. "
        + "(parent.shirt.color, parent.pants.color, parent.shoes.color, child.pet.color)");
    s.mapFieldNameAgainstSchema("color");
  }

  @Test
  public void objectWithoutType() throws SchemaException, IOException {
    expect(SchemaException.class, "should be a Json object containing `type` field");
    new Schema("{ \"title\": \"T\", \"type\": \"object\", \"properties\": { "
        + "\"foo\": { \"type\": \"array\", \"items\" : {} } } }");
  }

  @Test
  public void arrayWithoutContent() throws SchemaException, IOException {
    expect(SchemaException.class, "Array type nodes require an items object");
    new Schema("{ \"title\": \"T\", \"type\": \"object\", \"properties\": { "
        + "\"foo\": { \"type\": \"array\" }"
        + "} }");
  }

  @Test
  public void recordFoundNodeNullType() throws Exception {
    Schema s = new Schema("{}");
    s.recordFoundNode(null, "items", "fieldName", Collections.emptyList());
  }

  @Test
  public void getItemsWithoutStartObject() throws Exception {
    JsonParser jp = new JsonFactory().createParser("{}");
    jp.nextToken();
    Schema s = new Schema("{}");
    expect(SchemaException.class, "should be a Json object");
    s.getItems(jp, Collections.emptyList());
  }

  @Test
  public void fieldWithNullType() {
    assertThat(new Schema.Field("path", null).getType(), is(""));
  }
}
