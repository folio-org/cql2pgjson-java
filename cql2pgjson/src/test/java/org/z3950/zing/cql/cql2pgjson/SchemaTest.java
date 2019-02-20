package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
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

  /* `type`, `properties` and `items` are structural keywords, but can still be used for attributes. */
  @Test
  @Parameters({
    "type",
    "parent.type",
    "properties",
    "parent.properties",
    "items",
    "parent.items",
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
  @Parameters({
    "child",                        // array (array is object array)
    "relatives",                    // array (array is string array)
    "parent.umbrellas",             // object.array (array is object array)
    "parent.hats",                  // object.array (array is string array)
    "parent.address.phoneNumbers",  // object.object.array
  })
  public void arrayFieldAsLeaf(String field) throws Exception, IOException {
    Schema s = new Schema(Util.getResource("complex.json"));
    assertThat(s.mapFieldNameAndTypeAgainstSchema(field, null).getPath(), is(field));
    assertThat(s.mapFieldNameAndTypeAgainstSchema(field, "string").getPath(), is(field));
    thrown.expect(QueryValidationException.class);
    s.mapFieldNameAndTypeAgainstSchema(field, "integer");
  }

  @Test
  @Parameters({
    "child.items",       // array.arrayitem
    "child.items.pet",   // array.arrayitem.array
    "child.name",        // array.string
    "child.pet",         // array.array
    "child.pet.name",    // array.array.string
    "parent.hats.type",  // object.array.string
  })
  public void arrayFieldAsNonLeaf(String field) throws Exception, IOException {
    Schema s = new Schema(Util.getResource("complex.json"));
    thrown.expect(QueryValidationException.class);
    s.mapFieldNameAndTypeAgainstSchema(field, null);
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
    "id",
    "metadata.id",
    "metadata.name",
    "query.term",
    "query.boolean.op",
    "query.boolean.right.term",
    "query.boolean.right.boolean.right.term",
    "query.boolean.right.boolean.left.term",
    "query.boolean.left.boolean.right.term",
    "subdir.relative.query.term",
    "windows.id",
  })
  public void testRef(String field) throws IOException, SchemaException, QueryValidationException {
    Schema s = new Schema(Util.getResource("refs.json"));
    assertThat(s.mapFieldNameAgainstSchema(field).getPath(), is(field));
  }

  @Test
  public void testRefHttp() throws Exception {
    thrown.expect(QueryValidationException.class);
    thrown.expectMessage("'http.term' is not present in index");
    new Schema(Util.getResource("refs.json")).mapFieldNameAgainstSchema("http.term");
  }

  @Test
  public void recurseRefUriSyntaxException() throws Exception {
    thrown.expect(QueryValidationException.class);
    new Schema(Util.getResource("ref-uri-syntax.json")).mapFieldNameAgainstSchema("badref.x");
  }

  @Test
  public void recurseRefUriWithoutPath() throws Exception {
    thrown.expect(QueryValidationException.class);
    thrown.expectCause(is(instanceOf(IOException.class)));
    thrown.expectMessage("Cannot find target");
    new Schema(Util.getResource("ref-without-path.json")).mapFieldNameAgainstSchema("badref.x");
  }

  @Test
  public void recurseRefUriWithoutTargetPath() throws Exception {
    thrown.expect(QueryValidationException.class);
    thrown.expectCause(is(instanceOf(IOException.class)));
    thrown.expectMessage("Cannot find target");
    new Schema(Util.getResource("ref-without-target-path.json")).mapFieldNameAgainstSchema("badref.x");
  }

  @Test
  public void recurseRefUriMissingResource() throws Exception {
    thrown.expect(QueryValidationException.class);
    thrown.expectCause(is(instanceOf(IOException.class)));
    thrown.expectMessage("Cannot get resource");
    new Schema(Util.getResource("ref-with-missing-resource.json")).mapFieldNameAgainstSchema("badref.x");
  }

  @Test
  public void fieldWithNullType() {
    assertThat(new Schema.Field("path", null).getType(), is(""));
  }
}
