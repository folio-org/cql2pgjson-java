package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class SchemaTest {

  @Test
  public void validFieldLookupsTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    assertEquals(s.mapFieldNameAgainstSchema("city"),          "address.city");
    assertEquals(s.mapFieldNameAgainstSchema("address.city"),  "address.city");
    assertEquals(s.mapFieldNameAgainstSchema("zip"),           "address.zip");
    assertEquals(s.mapFieldNameAgainstSchema("address.zip"),   "address.zip");
    assertEquals(s.mapFieldNameAgainstSchema("name"),          "name");
  }

  /* Both `type` and `properties` are structural keywords, but can still be used for attributes. */
  @Test
  public void noReservedWordsTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("complex.json") );
    assertEquals(s.mapFieldNameAgainstSchema("type"),          "child.pet.type");
    assertEquals(s.mapFieldNameAgainstSchema("pet.type"),      "child.pet.type");
    assertEquals(s.mapFieldNameAgainstSchema("child.pet.type"),"child.pet.type");
    assertEquals(s.mapFieldNameAgainstSchema("properties"),    "parent.properties");    
    assertEquals(s.mapFieldNameAgainstSchema("favoriteColor"), "child.favoriteColor");    
  }

  @Test
  public void validFieldsWithTypeSpeficiedTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("userdata.json") );
    assertEquals(s.mapFieldNameAndTypeAgainstSchema("city","string"),        "address.city");
    assertEquals(s.mapFieldNameAndTypeAgainstSchema("address.city","string"),"address.city");
    assertEquals(s.mapFieldNameAndTypeAgainstSchema("name","string"),        "name");
    s = new Schema( Util.getResource("complex.json") ); // other size attributes are integers
    assertEquals(s.mapFieldNameAndTypeAgainstSchema("size","string"),        "parent.shirt.size");
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

  @Test(expected=QueryAmbiguousExeption.class)
  public void ambiguousQueryTest() throws QueryValidationException, IOException, SchemaException {
    Schema s = new Schema( Util.getResource("complex.json") );
    s.mapFieldNameAgainstSchema("size");
  }
  @Test(expected=QueryAmbiguousExeption.class)
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
    } catch (QueryAmbiguousExeption e) {
      assertEquals(e.getMessage(),
          "Field name 'color' was ambiguous in index. "
          + "(parent.shirt.color, parent.pants.color, parent.shoes.color, child.pet.color)");
    }
  }
}
