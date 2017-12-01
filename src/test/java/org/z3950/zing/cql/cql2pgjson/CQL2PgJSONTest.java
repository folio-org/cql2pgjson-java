package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class CQL2PgJSONTest extends DatabaseTestBase {
  private static CQL2PgJSON cql2pgJson;

  @BeforeClass
  public static void runOnceBeforeClass() throws Exception {
    setupDatabase();
    runSqlFile("users.sql");
    cql2pgJson = new CQL2PgJSON("users.user_data", Util.getResource("userdata.json"), Arrays.asList("name", "email"));
  }

  @AfterClass
  public static void runOnceAfterClass() {
    closeDatabase();
  }

  public void select(CQL2PgJSON aCql2pgJson, String sqlFile, String testcase) {
    int hash = testcase.indexOf('#');
    assertTrue("hash character in testcase", hash>=0);
    String cql           = testcase.substring(0, hash).trim();
    String expectedNames = testcase.substring(hash+1).trim();

    if (! cql.contains(" sortBy ")) {
      cql += " sortBy name";
    }
    String sql = null;
    try {
      String where = aCql2pgJson.cql2pgJson(cql);
      sql = "select user_data->'name' from users where " + where;
      runSqlFile(sqlFile);
      String actualNames = "";
      try ( Statement statement = conn.createStatement();
            ResultSet result = statement.executeQuery(sql) ) {

        while (result.next()) {
          if (! actualNames.isEmpty()) {
            actualNames += "; ";
          }
          actualNames += result.getString(1).replace("\"", "");
        }
      }
      assertEquals("CQL: " + cql + ", SQL: " + where, expectedNames, actualNames);
    } catch (QueryValidationException|SQLException e) {
      throw new RuntimeException(sql != null ? sql : cql, e);
    }
  }

  public void select(String sqlFile, String testcase) {
    select(cql2pgJson, sqlFile, testcase);
  }

  public void select(String testcase) {
    select(cql2pgJson, "jo-ka-lea.sql", testcase);
  }

  public void select(CQL2PgJSON aCql2pgJson, String testcase) {
    select(aCql2pgJson, "jo-ka-lea.sql", testcase);
  }

  /**
   * Invoke CQL2PgJSON.cql2pgJson(cql) expecting an exception.
   * @param cql  the cql expression that should trigger the exception
   * @param clazz  the expected class of the exception
   * @param contains  the expected strings of the exception message
   * @throws RuntimeException  if an exception was thrown that is not an instance of clazz
   */
  public void cql2pgJsonException(String cql,
      Class<? extends Exception> clazz, String ... contains) throws RuntimeException {
    try {
      CQL2PgJSON cql2pgJson = new CQL2PgJSON("users.user_data", Arrays.asList("name", "email"));
      cql2pgJsonException(cql2pgJson, cql, clazz, contains);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Invoke CQL2PgJSON.cql2pgJson(cql) expecting an exception.
   * @param cql2pgJson  the CQL2PgJSON to use
   * @param cql  the cql expression that should trigger the exception
   * @param clazz  the expected class of the exception
   * @param contains  the expected strings of the exception message
   * @throws RuntimeException  if an exception was thrown that is not an instance of clazz
   */
  public void cql2pgJsonException(CQL2PgJSON cql2pgJson, String cql,
      Class<? extends Exception> clazz, String ... contains) throws RuntimeException {
    try {
      cql2pgJson.cql2pgJson(cql);
    } catch (Throwable e) {
      if (! clazz.isInstance(e)) {
        throw new RuntimeException(e);
      }
      for (String s : contains) {
        assertTrue("Expect exception message containing '" + s + "': " + e.getMessage(),
            e.getMessage().toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT)));
      }
      return;
    }
    fail("Exception " + clazz + " expected.");
  }

  @Test
  @Parameters({
    "name=Long                      # Lea Long",
    "address.zip=2791               # Lea Long",
    "\"Lea Long\"                   # Lea Long",
    "\"Long Lea\"                   # Lea Long",
    "\"Long Lea Long\"              # Lea Long",
    "Long                           # Lea Long",
    "Lon                            #",
    "ong                            #",
    "jo@example.com                 # Jo Jane",
    "example                        # Jo Jane; Ka Keller; Lea Long",
    "email=example.com              # Jo Jane; Ka Keller; Lea Long",
    "email=\"example com\"          # Jo Jane; Ka Keller; Lea Long",
    "email=\"com example\"          # Jo Jane; Ka Keller; Lea Long",
    "email==example.com             #",
    "email<>example.com             # Jo Jane; Ka Keller; Lea Long",
    "name == \"Lea Long\"           # Lea Long",
    "name <> \"Lea Long\"           # Jo Jane; Ka Keller",
  })
  public void basic(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "name=*o*                                   # Jo Jane; Lea Long",
    "              email=*a                     # Ka Keller; Lea Long",
    "                           address.zip=*0  # Jo Jane; Ka Keller",
    "name=*o* and  email=*a                     # Lea Long",
    "name=*o* or   email=*a                     # Jo Jane; Ka Keller; Lea Long",
    "name=*o* not  email=*a                     # Jo Jane",
    "name=*o* and  email=*a or  address.zip=*0  # Jo Jane; Ka Keller; Lea Long",
    "name=*o* and (email=*a or  address.zip=*0) # Jo Jane; Lea Long",
    "name=*o* or   email=*a and address.zip=*0  # Jo Jane; Ka Keller",
    "name=*o* or  (email=*a and address.zip=*0) # Jo Jane; Ka Keller; Lea Long",
    "name=*o* not  email=*a or  address.zip=*0  # Jo Jane; Ka Keller",
    "name=*o* not (email=*a or  address.zip=*0) #",
    "name=*o* or   email=*a not address.zip=*0  # Lea Long",
    "name=*o* or  (email=*a not address.zip=*0) # Jo Jane; Lea Long",
    "\"lea example\"                            # Lea Long",  // both matches email
    "\"long example\"                           #",  // no match because "long" from name and "example" from email
  })
  public void andOrNot(String testcase) {
    select(testcase);
  }

  /** https://issues.folio.org/browse/DMOD-184 CQL conversion seems to ignore some errors */
  @Test
  public void startsWithOr() {
    cql2pgJsonException("or name=a", QueryValidationException.class);
  }

  @Test
  public void prox() {
    cql2pgJsonException("name=Lea prox/unit=word/distance>3 name=Long",
        CQLFeatureUnsupportedException.class, "CQLProxNode");
  }

  @Test
  @Parameters({
    "long                           # Lea Long",
    "LONG                           # Lea Long",
    "lONG                           # Lea Long",
    "email=JO                       # Jo Jane",
    "\"lEA LoNg\"                   # Lea Long",
    "name == \"LEA long\"           # Lea Long",
  })
  public void caseInsensitive(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "name=/respectCase   Long         # Lea Long",
    "name=/respectCase   long         #",
    "name=/respectCase   lonG         #",
    "name=/respectCase \"Long\"       # Lea Long",
    "name=/respectCase \"long\"       #",
    "name=/respectCase \"lonG\"       #",
  })
  public void caseSensitive(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "*Lea* *Long*                   # Lea Long",
    "*e* *on*                       # Lea Long",
    "?e? ?on?                       # Lea Long",
    "L*e*a L*o*n*g                  # Lea Long",
    "Lo??                           # Lea Long",
    "Lo?                            #",
    "Lo???                          #",
    "??a                            # Lea Long",
    "???a                           #",
    "?a                             # Ka Keller", // and not Lea
    "name=/masked ?a                # Ka Keller",
  })
  public void wildcards(String testcase) {
    select(testcase);
  }

  @Test
  public void masking() {
    cql2pgJsonException("name=/unmasked Lea",  CQLFeatureUnsupportedException.class, "unmasked");
    cql2pgJsonException("name=/substring Lea", CQLFeatureUnsupportedException.class, "substring");
    cql2pgJsonException("name=/regexp Lea",    CQLFeatureUnsupportedException.class, "regexp");
  }

  @Test
  @Parameters({
    "email==\\\\                    # a",
    "email==\\\\\\\\                # b",
    "email==\\*                     # c",
    "email==\\*\\*                  # d",
    "email==\\?                     # e",
    "email==\\?\\?                  # f",
    "email==\\\\\\\"                # g",
    "email==\\\\\\\"\\\\\\\"        # h",
    "             address.zip=1     # a",
    "'         OR address.zip=1     # a",
    "name=='   OR address.zip=1     # a",
    "name==\\  OR address.zip=1     # a",
    "\\a                            # a",
  })
  public void special(String testcase) {
    select("special.sql", testcase);
    select("special.sql", testcase.replace("==", "==/respectCase/respectAccents "));
  }

  @Test
  @Parameters({
    // The = operator is word based. An empty string or string with whitespace only contains no word at all so
    // there is no matching restriction - resulting in matching anything (that is defined and not null).
    "email=\"\"                         # e2; e3; e4",
    "email=\" \t \t \"                  # e2; e3; e4",
    "email==\"\"                        # e2",      // the == operator matches the complete string
    "email<>\"\"                        # e3; e4",  // the <> operator matches the complete string
    "address.city =  \"\"               # c2; c3; c4",
    "address.city == \"\"               # c2",
    "address.city <> \"\"               # c3; c4",  // same as example from CQL spec: dc.identifier <> ""
    "email=e                            # e4",
    "cql.allRecords=1 NOT email=e       # c0; c1; c2; c3; c4; e1; e2; e3; n",
    "email=\"\"       NOT email=e       # e2; e3",
  })
  public void fieldExistsOrEmpty(String testcase) {
    select("existsEmpty.sql", testcase);
  }

  @Test
  @Parameters({
    "                     lang ==/respectAccents []     # a",
    "cql.allRecords=1 NOT lang <>/respectAccents []     # a; n",
    "lang =/respectCase/respectAccents en               # b; c; d; f; g; h; i",

    // note that \"en\" also matches case f ["\"en"]
    "                     lang =/respectCase/respectAccents \\\"en\\\"   # b; f; i",  // without Java quoting: \"en\"
    "cql.allRecords=1 NOT lang =/respectCase/respectAccents \\\"en\\\"   # a; c; d; e; g; h; n",
    "lang = \"\"      NOT lang =/respectCase/respectAccents \\\"en\\\"   # a; c; d; e; g; h",
    "lang = \"\"                                                         # a; b; c; d; e; f; g; h; i",
    "cql.allRecords=1 NOT lang = \"\"                                    # n",
  })
  public void array(String testcase) {
    select("array.sql", testcase);
  }

  @Test
  @Parameters({
    "^Jo                            # Jo Jane",
    "^Jane                          #",
    "Jo^                            #",
    "Jane^                          # Jo Jane",
    "Jane^ ^Jo                      # Jo Jane",
  })
  public void caret(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "address.city= Søvang            # Lea Long",
    "address.city==Søvang            # Lea Long",
    "address.city= øvang             #",
    "address.city==øvang             #",
    "address.city= vang              #",
    "address.city= S?vang            # Lea Long",
    "address.city= S*vang            # Lea Long",
    "address.city= *ang              # Lea Long",
    "address.city= SØvang            # Lea Long",
    "address.city==SØvang            # Lea Long",
    "address.city= Sovang            # Lea Long",
    "address.city==Sovang            # Lea Long",
    "address.city= Sövang            # Lea Long",
    "address.city==Sövang            # Lea Long",
    "address.city= SÖvang            # Lea Long",
    "address.city==SÖvang            # Lea Long",
    "address.city= Sävang            #",
    "address.city==Sävang            #",
    "address.city= SÄvang            #",
    "address.city==SÄvang            #",
  })
  public void unicode(String testcase) {
    select(testcase);
    select(testcase.replace("==", "==/ignoreCase/ignoreAccents ")
                   .replace("= ", "= /ignoreCase/ignoreAccents "));
  }

  @Test
  @Parameters({
    "address.city= /respectCase Søvang # Lea Long",
    "address.city==/respectCase Søvang # Lea Long",
    "address.city= /respectCase SØvang #",
    "address.city==/respectCase SØvang #",
    "address.city= /respectCase Sovang # Lea Long",
    "address.city==/respectCase Sovang # Lea Long",
    "address.city= /respectCase SOvang #",
    "address.city==/respectCase SOvang #",
    "address.city= /respectCase Sövang # Lea Long",
    "address.city==/respectCase Sövang # Lea Long",
    "address.city= /respectCase SÖvang #",
    "address.city==/respectCase SÖvang #",
  })
  public void unicodeCase(String testcase) {
    select(testcase);
  }

  @Ignore("Needs locale/collation. Currently is C.")
  @Test
  @Parameters({
    "address.city= /respectAccents SØvang # Lea Long",
    "address.city==/respectAccents SØvang # Lea Long",
 })
  public void unicodeAccentsNonWindows(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "address.city= /respectAccents Søvang # Lea Long",
    "address.city==/respectAccents Søvang # Lea Long",
    "address.city= /respectAccents Sovang #",
    "address.city==/respectAccents Sovang #",
    "address.city= /respectAccents SOvang #",
    "address.city==/respectAccents SOvang #",
    "address.city= /respectAccents Sövang #",
    "address.city==/respectAccents Sövang #",
    "address.city= /respectAccents SÖvang #",
    "address.city==/respectAccents SÖvang #",
  })
  public void unicodeAccents(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "address.city= /respectCase/respectAccents Søvang # Lea Long",
    "address.city==/respectCase/respectAccents Søvang # Lea Long",
    "address.city= /respectCase/respectAccents SØvang #",
    "address.city==/respectCase/respectAccents SØvang #",
    "address.city= /respectCase/respectAccents Sovang #",
    "address.city==/respectCase/respectAccents Sovang #",
    "address.city= /respectCase/respectAccents SOvang #",
    "address.city==/respectCase/respectAccents SOvang #",
    "address.city= /respectCase/respectAccents Sövang #",
    "address.city==/respectCase/respectAccents Sövang #",
    "address.city= /respectCase/respectAccents SÖvang #",
    "address.city==/respectCase/respectAccents SÖvang #",
  })
  public void unicodeCaseAccents(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "address.city==/respectCase/respectAccents S*      # Jo Jane; Lea Long",
    "address.city<>/respectCase/respectAccents S*      # Ka Keller",
    "address.city==/respectCase/respectAccents S*g     # Lea Long",
    "address.city<>/respectCase/respectAccents S*g     # Jo Jane; Ka Keller",
    "address.city==/respectCase/respectAccents Sø*     # Lea Long",
    "address.city==/respectCase/respectAccents Sø*g    # Lea Long",
    "address.city==/respectCase/respectAccents ?øvang  # Lea Long",
    "address.city==/respectCase/respectAccents S?vang  # Lea Long",
    "address.city==/respectCase/respectAccents Søvan?  # Lea Long",
    "address.city==/respectCase/respectAccents *Søvang # Lea Long",
    "address.city==/respectCase/respectAccents **v**   # Jo Jane; Lea Long",
    "address.city==/respectCase/respectAccents **?y?** # Jo Jane",
    "address.city==/respectCase/respectAccents søvang  #",         // lowercase
  })
  public void like(String testcase) throws QueryValidationException {
    select(testcase);
    String sql = cql2pgJson.cql2pgJson(testcase.substring(0, testcase.indexOf('#')));
    assertThat(sql, containsString(" LIKE "));
  }

  @Test
  @Parameters({
    "example   sortBy name                         # Jo Jane; Ka Keller; Lea Long",
    "example   sortBy name/sort.ascending          # Jo Jane; Ka Keller; Lea Long",
    "example   sortBy name/sort.descending         # Lea Long; Ka Keller; Jo Jane",
    "example   sortBy address.zip                  # Ka Keller; Jo Jane; Lea Long",
    "name==*a* sortBy name                         # Jo Jane; Ka Keller; Lea Long",
    "name==*a* sortBy name/sort.ascending          # Jo Jane; Ka Keller; Lea Long",
    "name==*a* sortBy name/sort.descending         # Lea Long; Ka Keller; Jo Jane",
    "name==*a* sortBy address.zip                  # Ka Keller; Jo Jane; Lea Long",
  })
  public void sort(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "cql.allRecords=1 sortBy address.zip name  # a; b; c; d; e; f; g; h",
  })
  public void sortNumber(String testcase) {
    select("special.sql", testcase);
  }

  @Test
  @Parameters({
    "address.zip<1                  #",
    "address.zip<2                  # a",
    "address.zip<3                  # a; b",
    "address.zip<=0                 #",
    "address.zip<=1                 # a",
    "address.zip<=2                 # a; b",
    "address.zip>16                 # g; h",
    "address.zip>17                 # h",
    "address.zip>18                 #",
    "address.zip>=17                # g; h",
    "address.zip>=18                # h",
    "address.zip>=19                #",
    "address.zip<>4                 # a; b; c; g; h",
    "address.zip<>4.0               # a; b; c; g; h",
    "address.zip<>4e0               # a; b; c; g; h",
    "address.zip=1                  # a",  // must not match 17, 18
    "address.zip==1                 # a",
  })
  public void compareNumber(String testcase) {
    select("special.sql", testcase);
  }

  @Test
  @Parameters({
    "address.zip<>4                 # a; b; c; g; h",
    "address.zip<>4.0               # a; b; c; g; h",
    "address.zip<>4e0               # a; b; c; g; h",
  })
  public void compareNumberNoSchema(String testcase) throws CQL2PgJSONException {
    CQL2PgJSON aCql2PgJson = new CQL2PgJSON("users.user_data");  // without schema
    select(aCql2PgJson, "special.sql", testcase);
  }

  @Test(expected = CQLFeatureUnsupportedException.class)
  public void compareNumberNotImplemented() throws Exception {
    // We test unreachable code because CQL2PgJSON.match(CQLTermNode) throws an exception
    // before. We test it anyway.
    CQLTermNode node = new CQLTermNode("zip", new CQLRelation("adj"), "12");
    CQL2PgJSON.getNumberMatch(node);
  }

  @Test
  @Parameters({
    "name< \"Ka Keller\"  # Jo Jane",
    "name<=\"Ka Keller\"  # Jo Jane; Ka Keller",
    "name> \"Ka Keller\"  # Lea Long",
    "name>=\"Ka Keller\"  # Ka Keller; Lea Long",
    "name<>\"Ka Keller\"  # Jo Jane; Lea Long",
  })
  public void compareString(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "name<>4              # Jo Jane; Ka Keller; Lea Long",
    "name=4               #",
  })
  public void compareStringNoSchema(String testcase) throws FieldException {
    CQL2PgJSON aCql2PgJson = new CQL2PgJSON("users.user_data");  // without schema
    select(aCql2PgJson, "jo-ka-lea.sql", testcase);
  }

  @Test
  @Parameters({
    "cql.allRecords=1                             # Jo Jane; Ka Keller; Lea Long",
    "cql.allRecords=1 NOT name=Jo                 # Ka Keller; Lea Long",
    "cql.allRecords=0                             # Jo Jane; Ka Keller; Lea Long",
    "cql.allRecords=0 OR name=Jo                  # Jo Jane; Ka Keller; Lea Long",
    "cql.allRecords=1 sortBy name/sort.descending # Lea Long; Ka Keller; Jo Jane",
  })
  public void allRecords(String testcase) {
    select(testcase);
  }

  @Test(expected = FieldException.class)
  public void nullField() throws FieldException {
    String s = null;
    new CQL2PgJSON(s);
  }

  @Test(expected = FieldException.class)
  public void emptyField() throws FieldException {
    new CQL2PgJSON(" \t \t ");
  }

  @Test
  public void noServerChoiceIndexes() throws IOException, CQL2PgJSONException {
    cql2pgJsonException(new CQL2PgJSON("users.user_data", Arrays.asList()),
        "Jane", QueryValidationException.class, "serverChoiceIndex");
    cql2pgJsonException(new CQL2PgJSON("users.user_data", (List<String>) null),
        "Jane", QueryValidationException.class, "serverChoiceIndex");
    cql2pgJsonException(new CQL2PgJSON("users.user_data", "{}"),
        "Jane", QueryValidationException.class, "serverChoiceIndex");
  }

  @Test
  public void prefixNotImplemented() throws FieldException, RuntimeException {
    cql2pgJsonException(new CQL2PgJSON("users.user_data"),
        "> n = name n=Ka", CQLFeatureUnsupportedException.class, "CQLPrefixNode");
  }

  @Test
  public void relationNotImplemented() throws FieldException, RuntimeException {
    cql2pgJsonException(new CQL2PgJSON("users.user_data"),
        "address.zip encloses 12", CQLFeatureUnsupportedException.class, "Relation", "encloses");
  }

  @Test(expected = ServerChoiceIndexesException.class)
  public void nullIndex() throws CQL2PgJSONException {
    new CQL2PgJSON("users.user_data", Arrays.asList((String) null));
  }

  @Test(expected = ServerChoiceIndexesException.class)
  public void emptyIndex() throws CQL2PgJSONException {
    new CQL2PgJSON("users.user_data", Arrays.asList(" \t \t "));
  }

  @Test(expected = ServerChoiceIndexesException.class)
  public void doubleQuoteIndex() throws CQL2PgJSONException {
    new CQL2PgJSON("users.user_data", Arrays.asList("test\"cql"));
  }

  @Test(expected = ServerChoiceIndexesException.class)
  public void singleQuoteIndex() throws CQL2PgJSONException {
    new CQL2PgJSON("users.user_data", Arrays.asList("test'cql"));
  }

  @Test(expected = FieldException.class)
  public void nullFieldList() throws CQL2PgJSONException {
    new CQL2PgJSON((List<String>) null);
  }

  @Test(expected = FieldException.class)
  public void emptyFieldList() throws CQL2PgJSONException {
    new CQL2PgJSON(Arrays.asList());
  }

  @Test(expected = FieldException.class)
  public void nullSchemaList() throws CQL2PgJSONException, IOException {
    new CQL2PgJSON((Map<String,String>) null);
  }

  @Test(expected = FieldException.class)
  public void emptySchemaList() throws CQL2PgJSONException, IOException {
    new CQL2PgJSON(Collections.emptyMap());
  }

  @Test(expected = FieldException.class)
  public void schemaListContainsNull() throws CQL2PgJSONException, IOException {
    Map<String,String> map = new HashMap<>();
    map.put(null,  null);
    new CQL2PgJSON(map);
  }

  private void schemaList(String fieldname) throws Exception {
    Map<String,String> map = new HashMap<>();
    map.put(fieldname,  "{}");
    new CQL2PgJSON(map);
  }

  @Test(expected = FieldException.class)
  public void schemaListWithNullFieldname() throws Exception {
    schemaList(null);
  }

  @Test(expected = FieldException.class)
  public void schemaListWithEmptyFieldname() throws Exception {
    schemaList("");
  }

  @Test(expected = FieldException.class)
  public void schemaListWithSpaceFieldname() throws Exception {
    schemaList(" ");
  }

  @Parameters({
    "name=Long                      # Lea Long",
    "address.zip=2791               # Lea Long",
    "zip=2791                       # Lea Long",
  })
  @Test
  public void schema(String testcase) throws IOException, CQL2PgJSONException {
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON("users.user_data", Util.getResource("userdata.json"));
    select(aCql2pgJson, testcase);
  }

  @Test
  public void notInSchema() throws IOException, CQL2PgJSONException {
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(
        "users.user_data", Util.getResource("userdata.json"), Arrays.asList("name"));
    cql2pgJsonException(aCql2pgJson, "foobar=x", QueryValidationException.class);
  }

  @Test
  public void sortIndexNotInSchema() throws IOException, CQL2PgJSONException {
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(
        "users.user_data", Util.getResource("userdata.json"), Arrays.asList("name"));
    cql2pgJsonException(aCql2pgJson, "name<>x sortBy notExistingIndex", QueryValidationException.class);
  }
}
