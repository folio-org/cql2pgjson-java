package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class SchemaTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
    "address.city",
    "address.zip",
    "name",
  })
  public void validFieldLookupsTest(String field) throws Exception {
    Schema s = new Schema(Util.getResource("userdata.json"));
    assertThat(s.mapFieldNameAgainstSchema(field).getPath(), is(field));
  }

  /* Both `type` and `properties` are structural keywords, but can still be used for attributes. */
  @Test
  @Parameters({
    "type",
    "child.pet.type",
    "properties",
    "parent.properties",
    "child.favoriteColor",
  })
  public void noReservedWordsTest(String field) throws Exception {
    Schema s = new Schema(Util.getResource("complex.json"));
    assertThat(s.mapFieldNameAgainstSchema(field).getPath(), is(field));
  }

  @Test
  @Parameters({
    "userdata.json, address.city",
    "userdata.json, name",
    "complex.json,  parent.shirt.size",  // other size attributes are integers
  })
  public void validFieldsWithTypeSpeficiedTest(String schema, String field) throws Exception {
    Schema s = new Schema(Util.getResource(schema));
    assertThat(s.mapFieldNameAndTypeAgainstSchema(field, "string").getPath(), is(field));
  }

  @Test
  public void fieldsWithTypeNotFound() throws Exception {
    Schema s = new Schema(Util.getResource("complex.json"));
    expect(QueryValidationException.class, "Field name", "size", "is not present");
    s.mapFieldNameAndTypeAgainstSchema("size", "date");
  }

  @Test
  public void invalidFieldWithoutPath() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    expect(QueryValidationException.class, "Field name", "city", "is not present");
    s.mapFieldNameAgainstSchema("city");  // correct with path: address.city
  }

  @Test
  public void inValidFieldsWithTypeSpeficiedTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    expect(QueryValidationException.class, "Field name", "city", "is not present");
    s.mapFieldNameAndTypeAgainstSchema("city","integer");
  }

  @Test
  @Parameters({
    "id,                                       id",
    "metadata.id,                              metadata.id",
    "metadata.name,                            metadata.name",
    "query.term,                               query.term",
    "query.boolean.op,                         query.boolean.op",
    "query.boolean.right.term,                 query.boolean.right.term",
    "query.boolean.right.boolean.left.term,    query.boolean.right.boolean.left.term",
    "query.prox.term,                          query.prox.term",
    "query.prox.prox.prox.term,                query.prox.prox.prox.term"
  })
  public void testRef(String field, String fullField) throws IOException, SchemaException, QueryValidationException {
    Schema s = new Schema(Util.getResource("refs.json"));
    assertThat(s.mapFieldNameAgainstSchema(field).getPath(), is(fullField));
  }

  @Test
  public void fieldWithNullType() {
    assertThat(new Schema.Field("path", null).getType(), is(""));
  }
}
