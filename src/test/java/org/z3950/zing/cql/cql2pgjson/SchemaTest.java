package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class SchemaTest {

  @Test
  public void ValidFieldLookupsTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    assertTrue(s.mapFieldNameAgainstSchema("city").equals("address.city"));
    assertTrue(s.mapFieldNameAgainstSchema("address.city").equals("address.city"));
    assertTrue(s.mapFieldNameAgainstSchema("zip").equals("address.zip"));
    assertTrue(s.mapFieldNameAgainstSchema("address.zip").equals("address.zip"));
    assertFalse(s.mapFieldNameAgainstSchema("name").equals("address.zip"));
    assertTrue(s.mapFieldNameAgainstSchema("name").equals("name"));
    assertFalse(s.mapFieldNameAgainstSchema("name").equals("address.name"));
    assertFalse(s.mapFieldNameAgainstSchema("name").equals("name.address"));
  }

  @Test
  public void ValidFieldsWithTypeSpeficiedTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    assertTrue(s.mapFieldNameAndTypeAgainstSchema("city","string").equals("address.city"));
    assertTrue(s.mapFieldNameAndTypeAgainstSchema("address.city","string").equals("address.city"));
    assertTrue(s.mapFieldNameAndTypeAgainstSchema("name","string").equals("name"));
  }

  @Test(expected=QueryValidationException.class)
  public void InvalidFieldLookupTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    s.mapFieldNameAgainstSchema("fake_field");
    fail("We shouldn't get here.");
  }

  @Test(expected=QueryValidationException.class)
  public void inValidFieldsWithTypeSpeficiedTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    s.mapFieldNameAndTypeAgainstSchema("city","integer");
    fail("We shouldn't get here.");
  }
}
