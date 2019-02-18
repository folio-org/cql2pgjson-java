package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

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
import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class CQL2PgJSONTest extends DatabaseTestBase {
  private static Logger logger = Logger.getLogger(CQL2PgJSONTest.class.getName());
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
    assertTrue("hash character in testcase", hash >= 0);
    String cql = testcase.substring(0, hash).trim();
    String expectedNames = testcase.substring(hash + 1).trim();
    select(aCql2pgJson, sqlFile, cql, expectedNames);
  }

  public void select(CQL2PgJSON aCql2pgJson, String sqlFile, String cql, String expectedNames) {

    if (! cql.contains(" sortBy ")) {
      cql += " sortBy name";
    }
    String sql = null;
    try {
      String blob = "user_data";
      String tablename = "users";
      // Dirty hack, the tests had the table name and blob name hard coded,
      // but the instances test uses different ones. Some day we may clean
      // this mess up
      if ("instances.jsonb".equals(aCql2pgJson.getjsonField())) {
        blob = "jsonb";
        tablename = "instances";
      }
      String where = aCql2pgJson.cql2pgJson(cql);
      //sql = "select user_data->'name' from users where " + where;
      sql = "select " + blob + "->'name' from " + tablename + " where " + where;
      logger.fine("select: cql: " + cql);
      logger.fine("select: sql:" + sql);
      runSqlFile(sqlFile);
      logger.fine("select: sqlfile done");
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
      if (!expectedNames.equals(actualNames)) {
        logger.fine("select: Test FAILURE on " + cql + "#" + expectedNames);
      }
      logger.fine("select: Got names [" + actualNames + "], expected [" + expectedNames + "]");
      assertEquals("CQL: " + cql + ", SQL: " + where, expectedNames, actualNames);
    } catch (QueryValidationException e) {
      logger.fine("select: QueryValidationException "
        + " for query " + cql + " : " + e.getMessage());
      if (expectedNames.isEmpty()) {
        throw new RuntimeException(sql != null ? sql : cql, e);
      }
      assertThat(e.toString(), containsString(expectedNames));
    } catch (SQLException e) {
      logger.fine("select: SQL Exception " + e.getMessage());
      throw new RuntimeException(sql != null ? sql : cql, e);
    }
    logger.fine("select: done with " + cql);
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
      if (!clazz.isInstance(e)) {
        logger.fine("Wrong exception. Expected " + clazz + ". " + "but got " + e);
        throw new RuntimeException(e);
      }
      for (String s : contains) {
        assertTrue("Expect exception message containing '" + s + "': " + e.getMessage(),
            e.getMessage().toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT)));
      }
      return;
    }
    fail("Exception " + clazz + " expected, but no exception thrown");
  }

  @Test
  @Parameters({
    "name=Long                      # Lea Long",
    "address.zip=2791               # Lea Long",
    "\"Lea Long\"                   # Lea Long",
    "\"Long Lea\"                   #",
    "Long                           # Lea Long",
    "Lon                            #",
    "ong                            #",
    "jo@example.com                 # Jo Jane",
    "example                        # Jo Jane; Ka Keller; Lea Long",
    "email=example.com              # Jo Jane; Ka Keller; Lea Long",
    "email=\"example com\"          # Jo Jane; Ka Keller; Lea Long",
    "email=\"com example\"          #",
    "email==example.com             #",
    "email<>example.com             # Jo Jane; Ka Keller; Lea Long",
    "email==ka@example.com          # Ka Keller",
    "name == \"Lea Long\"           # Lea Long",
    "name <> \"Lea Long\"           # Jo Jane; Ka Keller",
    "name=\"\"                      # Jo Jane; Ka Keller; Lea Long",
    "name=\"*\"                     # Jo Jane; Ka Keller; Lea Long",
    "name=*                         # Jo Jane; Ka Keller; Lea Long",
    "email=\"\"                     # Jo Jane; Ka Keller; Lea Long",
    "email=\"*\"                    # Jo Jane; Ka Keller; Lea Long",
    "email=*                        # Jo Jane; Ka Keller; Lea Long",})
  public void basic(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "name all \"\"                         # Jo Jane; Ka Keller; Lea Long",
    "name all Lea                          # Lea Long",
    "name all Long                         # Lea Long",
    "name all \"Lea Long\"                 # Lea Long",
    "name all \"Long Lea\"                 # Lea Long",
    "name all \"FooBar\"                   #",
    "email all \"com example ka\"          # Ka Keller",
    "email all \"com example\"             # Jo Jane; Ka Keller; Lea Long",
    "email all \"ka@example.\"             # Ka Keller",
    "email all \"@example.com\"            # Jo Jane; Ka Keller; Lea Long",
    "email all example.com                 # Jo Jane; Ka Keller; Lea Long",
    "email all/respectCase example.com     # Jo Jane; Ka Keller; Lea Long",
    "email all/respectCase Example.com     #",
    "email all/respectAccents Example.com  # Jo Jane; Ka Keller; Lea Long",
    "email all/respectAccents Exämple.com  #",
  })
  public void all(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "name any \"\"                         # Jo Jane; Ka Keller; Lea Long",
    "name any Lea                          # Lea Long",
    "name any Long                         # Lea Long",
    "name any \"Lea Long\"                 # Lea Long",
    "name any \"Long Lea\"                 # Lea Long",
    "name any \"Lea FooBar\"               # Lea Long",
    "name any \"FooBar Long\"              # Lea Long",
    "email any \"com example ka\"          # Jo Jane; Ka Keller; Lea Long",
    "email any \"com example\"             # Jo Jane; Ka Keller; Lea Long",
    "email any \"ka@example.\"             # Ka Keller",
    "email any \"@example.com\"            # Jo Jane; Ka Keller; Lea Long",
    "email any/respectCase example.com     # Jo Jane; Ka Keller; Lea Long",
    "email any/respectCase Example.com     #",
    "email any/respectAccents Example.com  # Jo Jane; Ka Keller; Lea Long",
    "email any/respectAccents Exämple.com  #",
  })
  public void any(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "name adj \"\"                         # Jo Jane; Ka Keller; Lea Long",
    "name adj Lea                          # Lea Long",
    "name adj Long                         # Lea Long",
    "name adj \"Lea Long\"                 # Lea Long",
    "name adj \"Long Lea\"                 #",
    "email adj \"ka example com\"          # Ka Keller",
    "email adj \"ka example\"              # Ka Keller",
    "email adj \"example com\"             # Jo Jane; Ka Keller; Lea Long",
    "email adj \"ka@example.com\"          # Ka Keller",
    "email adj \"ka@example\"              # Ka Keller",
    "email adj \"ka@example.\"             # Ka Keller",
    "email adj \"example.com\"             # Jo Jane; Ka Keller; Lea Long",
    "email adj \"@example.com\"            # Jo Jane; Ka Keller; Lea Long",
    "email adj example.com                 # Jo Jane; Ka Keller; Lea Long",
    "email adj/respectCase example.com     # Jo Jane; Ka Keller; Lea Long",
    "email adj/respectCase Example.com     #",
    "email adj/respectAccents Example.com  # Jo Jane; Ka Keller; Lea Long",
    "email adj/respectAccents Exämple.com  #",
  })
  public void adj(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({ // Can not to left trucn on names, it is fulltext
    "name=ja*                                   # Jo Jane",
    "name=lo*                                   # Lea Long",
    "              email=*a                     # Ka Keller; Lea Long",
    "                           address.zip=*0  # Jo Jane; Ka Keller",
    "name=lo* and  email=*a                     # Lea Long",
    "name=lo* or   email=*a                     # Ka Keller; Lea Long",
    "name=ja* not  email=*a                     # Jo Jane",
    "name=lo* and  email=*a or  address.zip=*0  # Jo Jane; Ka Keller; Lea Long",
    "name=lo* and (email=*a or  address.zip=*0) # Lea Long",
    "name=lo* or   email=*a and address.zip=*0  # Ka Keller",
    "name=lo* or  (email=*a and address.zip=*0) # Ka Keller; Lea Long",
    "name=lo* not  email=*a or  address.zip=*0  # Jo Jane; Ka Keller",
    "name=lo* not (email=*a or  address.zip=*0) #",
    "name=lo* or   email=*a not address.zip=*0  # Lea Long",
    "name=lo* or  (email=*a not address.zip=*0) # Lea Long",
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
    //"name == \"LEA long\"           # Lea Long",
    "name == \"Lea Long\"           # Lea Long",
    "name == \"LEA long\"           #", // == means exact match, case and everything
  })
  public void caseInsensitive(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "email=/respectCase   lea         # Lea Long",
    "email=/respectCase   lEa         # ",
    "email=/respectCase \"lea\"       # Lea Long",
    "email=/respectCase \"LEA\"       #"
  })
  public void caseSensitive(String testcase) {
    select(testcase);
  }

  @Test
  /* fulltext only supports right truncation
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
     */
  @Parameters({
    "name=Lea                       # Lea Long",
    "name=Long                      # Lea Long",
    "name=Lo*                       # Lea Long",
    "lon*                           # Lea Long", // email matches the old way
    "example*                       # Jo Jane; Ka Keller; Lea Long"
  })
  public void wildcards(String testcase) {
    select(testcase);
  }

  @Test
  public void masking() {
    cql2pgJsonException("email=/unmasked Lea", CQLFeatureUnsupportedException.class, "unmasked");
    cql2pgJsonException("email=/substring Lea", CQLFeatureUnsupportedException.class, "substring");
    cql2pgJsonException("email=/regexp Lea", CQLFeatureUnsupportedException.class, "regexp");
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
    "h                              # h",
    //"a                              # ", // 'a' is a stop word, tokenized away, when using 'english'
    //"\\a                            # ",
    "a                              # a", // but not when using 'simple'
    "\\a                            # a",
    "\\h                            # h"
  })
  public void special(String testcase) {
    select("special.sql", testcase);
    //select("special.sql", testcase.replace("==", "==/respectCase/respectAccents "));
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
    "cql.allRecords=1 NOT email=\"\"    # c0; c1; c2; c3; c4; e1; n",
    "cql.allRecords=1 NOT email==\"\"   # c0; c1; c2; c3; c4; e1; e3; e4; n",
    "email=\"\"       NOT email==\"\"   # e3; e4",
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
  // Should not produce a StackOverflowError:
  // https://issues.folio.org/browse/CIRC-119 "Requests API GET /requests does not scale"
  public void matchAnyFromLongList() {
    select("name==(a or b or c or d or e or f or g or h or j or k or l or m or n or o or p or q or s or t or u or v "
      + "or w or x or y or z or 0 or 1 or 2 or 3 or 4 or 5 or 6 or 7 or 8 or 9 or \"Jo Jane\")  # Jo Jane");
  }

  //@Test
  // The fulltext does not support caret anchoring. We do not have enough
  // data in the email field to make a maeningful test. Anyway, the serverchoice
  // tries to match the name first, and that throws a QueryValidationException
  // on the caret, so we never try the old-fashioned way.
  @Parameters({
    "^Jo                            # Jo Jane",
    "Jo^                            #",
    "Jo^ Jane                       #",
    "^Jane                          #",
    "Jo ^Jane                       #",
    "Jane^                          # Jo Jane",
    "^Jo Jane^                      # Jo Jane",
    "name any \"Jane^ ^Jo\"         # Jo Jane",
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
    "name=\"\" sortBy name                         # Jo Jane; Ka Keller; Lea Long",
    "name=\"\" sortBy name/sort.ascending          # Jo Jane; Ka Keller; Lea Long",
    "name=\"\" sortBy name/sort.descending         # Lea Long; Ka Keller; Jo Jane",
    "name=\"\" sortBy address.zip                  # Ka Keller; Jo Jane; Lea Long"
  })
  public void sort(String testcase) {
    select(testcase);
  }

  @Test
  @Parameters({
    "email=example   sortBy name                   # Jo Jane; Ka Keller; Lea Long",
    "email=example   sortBy name/sort.ascending    # Jo Jane; Ka Keller; Lea Long",
    "email=example   sortBy name/sort.descending   # Lea Long; Ka Keller; Jo Jane",
    "email=example   sortBy address.zip            # Ka Keller; Jo Jane; Lea Long",
    "name=\"\" sortBy name                         # Jo Jane; Ka Keller; Lea Long",
    "name=\"\" sortBy name/sort.ascending          # Jo Jane; Ka Keller; Lea Long",
    "name=\"\" sortBy name/sort.descending         # Lea Long; Ka Keller; Jo Jane",
    "name=\"\" sortBy address.zip                  # Ka Keller; Jo Jane; Lea Long"
  })
  public void sortNoSchema(String testcase) throws FieldException {
    CQL2PgJSON aCql2PgJson = new CQL2PgJSON("users.user_data");  // without schema
    select(aCql2PgJson, testcase);
  }

  @Test
  @Parameters({
    "cql.allRecords=1 sortBy address.zip/sort.ascending  name  # a; b; c; d; e; f; g; h",
    "cql.allRecords=1 sortBy address.zip/sort.descending name  # h; g; d; e; f; c; b; a",
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
    "address.zip= 4                 # d; e; f",
    "address.zip==4                 # d; e; f",
    "address.zip= 4.0               # d; e; f",
    "address.zip==4.0               # d; e; f",
    "address.zip= 4e0               # d; e; f",
    "address.zip==4e0               # d; e; f",
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
    "address.zip= 4                 # d; e; f",   // because 4 is a word in "4.0" and "4e0"
    "address.zip==4                 # d; e; f",
    "address.zip= 4.0               # e",
    "address.zip==4.0               # d; e; f",
    "address.zip= 4e0               #",           // 4e0 is stored as 4, so it cannot match
    "address.zip==4e0               # d; e; f",
    "address.zip<>4                 # a; b; c; g; h",
    "address.zip<>4.0               # a; b; c; g; h",
    "address.zip<>4e0               # a; b; c; g; h",
  })
  public void compareNumberNoSchema(String testcase) throws CQL2PgJSONException {
    CQL2PgJSON aCql2PgJson = new CQL2PgJSON("users.user_data");  // without schema
    select(aCql2PgJson, "special.sql", testcase);
  }

  @Test
  @Parameters({
    "address.city =    1234                 # e; f",
    "address.city all  1234                 # e; f",
    "address.city ==   1234                 # e",
    "address.city =   01234                 # g; h",
    "address.city all 01234                 # g; h",
    "address.city ==  01234                 # g",
  })
  public void numberInStringField(String testcase) throws CQL2PgJSONException {
    // with schema
    select("special.sql", testcase);

    // without schema
    CQL2PgJSON aCql2PgJson = new CQL2PgJSON("users.user_data");
    select(aCql2PgJson, "special.sql", testcase);
  }

  @Test
  public void numberInSchema() throws CQL2PgJSONException {
    select("number=4.0  # Jo Jane");
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

  @Test
  public void schemaListWithEmptySchema() throws Exception {
    Map<String,String> map = new HashMap<>();
    map.put("fieldname", "");
    new CQL2PgJSON(map);
  }

  @Test
  public void schemaListOneField() throws Exception {
    Map<String,String> map = new HashMap<>();
    map.put("users.user_data", Util.getResource("userdata.json"));
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(map, Arrays.asList("name"));
    select(aCql2pgJson, "Long   # Lea Long");
  }

  @Test
  public void schemaListTwoFields() throws Exception {
    Map<String,String> map = new HashMap<>();
    map.put("users.user_data", Util.getResource("userdata.json"));
    map.put("users.user_data2", Util.getResource("userdata.json"));
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(map, Arrays.asList("name"));
    select(aCql2pgJson, "Long   # Lea Long");
  }

  @Test
  public void singleField() throws CQL2PgJSONException {
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(Arrays.asList("users.user_data"), Arrays.asList("name"));
    select(aCql2pgJson, "Long   # Lea Long");
  }

  @Parameters({
    "name=Long                      # Lea Long",
    "address.zip=2791               # Lea Long",
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

  @Test
  public void toSqlSimple() throws QueryValidationException {
    SqlSelect s = cql2pgJson.toSql("cql.allRecords=1");
    assertEquals("true", s.getWhere());
    assertEquals("", s.getOrderBy());
    assertEquals("WHERE true", s.toString());
  }

  @Test
  public void toSql() throws QueryValidationException {
    SqlSelect s = cql2pgJson.toSql("email=Long sortBy name/sort.descending");
    assertEquals("lower(f_unaccent(users.user_data->>'email')) ~", s.getWhere().substring(0, 46));
    assertEquals("lower(f_unaccent(users.user_data->>'name')) DESC", s.getOrderBy());
    String sql = s.toString();
    assertTrue(sql.startsWith("WHERE "
      + "lower(f_unaccent(users.user_data->>'email')) ~"));
    assertTrue(sql.endsWith(" ORDER BY "
      + "lower(f_unaccent(users.user_data->>'name')) DESC"));
  }
  @Test
  public void optimizedOR() throws QueryValidationException {
    SqlSelect s = cql2pgJson.toSql("name=* OR email=*");
    assertEquals("true", s.getWhere());
    s = cql2pgJson.toSql("name=* OR email=* OR zip=*");
    assertEquals("true", s.getWhere());
    s = cql2pgJson.toSql("name=\"\"");  // any that has a name
    assertEquals("users.user_data->>'name' ~ ''", s.getWhere());
    s = cql2pgJson.toSql("name=\"\" OR email=\"\"");
    assertEquals("(users.user_data->>'name' ~ '') OR (users.user_data->>'email' ~ '')", s.getWhere());
  }

  @Test(expected = QueryValidationException.class)
  public void toSqlException() throws QueryValidationException {
    cql2pgJson.toSql("");
  }

  @Test
  @Parameters({
    "id=*,                                        true",
    "id=\"11111111-1111-1111-1111-111111111111\",  _id='11111111-1111-1111-1111-111111111111'",
    "id=\"2*\",                                  (_id>='20000000-0000-0000-0000-000000000000' and "
                                               + "_id<='2fffffff-ffff-ffff-ffff-ffffffffffff')",
    "id=\"22222222*\",                           (_id>='22222222-0000-0000-0000-000000000000' and "
                                               + "_id<='22222222-ffff-ffff-ffff-ffffffffffff')",
  })
  public void pKeySql(String cql, String expectedSql) throws QueryValidationException {
    assertEquals(expectedSql, cql2pgJson.toSql(cql).getWhere());
    assertEquals(expectedSql, cql2pgJson.toSql(cql.replace("=", "==")).getWhere());
  }

  @Test
  public void pKeyDefault() throws CQL2PgJSONException {
    CQL2PgJSON c = new CQL2PgJSON("users.user_data");
    c.dbTable.remove("pkColumnName");
    String sql = c.toSql("id=\"11111111-1111-1111-1111-111111111111\"").getWhere();
    // default pkColumnName is id without underscore
    assertEquals("id='11111111-1111-1111-1111-111111111111'", sql);
  }

  @Test
  @Parameters({
    "id=11111111-1111-1111-1111-111111111111   # Jo Jane",
    "id=22222222-2222-2222-2222-222222222222   # Ka Keller",
    "id=33333333-3333-3333-3333-33333333333a   # Lea Long",
    "id=33333333-3333-3333-3333-33333333333A   # Lea Long",
    "id==33333333-3333-3333-3333-33333333333A  # Lea Long",
    "id<>11111111-1111-1111-1111-111111111111  # Ka Keller; Lea Long",
    "id<>22222222-2222-2222-2222-222222222222  # Jo Jane; Lea Long",
    "id<>33333333-3333-3333-3333-33333333333A  # Jo Jane; Ka Keller",
    "id=zz                                     #",         // invalid UUID doesn't match any record
    "id==zz                                    #",
    "id<>zz                                    # Jo Jane; Ka Keller; Lea Long",
    "id=11111111111111111111111111111111       #",
    "id=11111111+1111-1111-1111-111111111111   #",
    "id=11111111-1111-1111-1111-11111111111    #",
    "id=11111111-1111-1111-1111-1111111111111  #",
    "id=11111111-1111-1111-1111-111111111111-1 #",
    "id=1*                                     # Jo Jane",
    "id=1z*                                    #",
    "id<>1*                                    # Ka Keller; Lea Long",
    "id<>2*                                    # Jo Jane; Lea Long",
    "id<>3*                                    # Jo Jane; Ka Keller",
    "id=11111111-1111-1111-1111-111111111111*  # Jo Jane", // ok to trunc after full match, the UI does
    "id=*                                      # Jo Jane; Ka Keller; Lea Long",
    "id<>*                                     #",
    "id=*1                                     # only right truncation supported for id",
    "id=*1*                                    # only right truncation supported for id",
    "id=11111111*1111-1111-1111-111111111111   # only right truncation supported for id",
    "id>                11111111-1111-1111-1111-111111111111   # Unsupported operator",
    "id=/ignoreCase     11111111-1111-1111-1111-111111111111   # Unsupported modifier ignorecase",
    "id=/respectCase    11111111-1111-1111-1111-111111111111   # Unsupported modifier respectcase",
    "id=/masked         11111111-1111-1111-1111-111111111111   # Unsupported modifier masked",
    "id=/regexp         11111111-1111-1111-1111-111111111111   # Unsupported modifier regexp",
    "id=/respectAccents 11111111-1111-1111-1111-111111111111   # Unsupported modifier respectaccents",
    "id=/ignoreAccents  11111111-1111-1111-1111-111111111111   # Unsupported modifier ignoreaccents",
  })
  public void pKey(String testcase) {
    select(cql2pgJson, testcase);
  }

  //
  // Fulltext search tests
  //
  @Test
  @Parameters({
    "name=Long                      # Lea Long",
    "address.zip=2791               # Lea Long",
    "\"Lea Long\"                   # Lea Long",
    "\"Long Lea\"                   #",
    "Long                           # Lea Long",
    "Lon                            #",
    "ong                            #",
    "cql.serverChoice==example      #", // == means exact match
    "email==ka@example.com          # Ka Keller",
    "email=ka@example.com           # Ka Keller",
    "email=ka@*                     # Ka Keller",
    // PG tries to be clever in tokenizing emails. Anything that looks
    // like (.+)@(.*)(\.)(??.+) is considered an email, and comes up
    // as one token. Anything else will be tokenized at the '@' and '.'
    // into separate tokens. The end result is that email-looking things
    // will only do exact match, and truncation breaks (unless the top-domain
    // is longer than two characters, and we mention two characters and
    // truncate the rest)
    //
    // These tests work the same way as before:
    "jo@example.com                   # Jo Jane", // complete email works
    "email=\"com example\"            #",
    "email==example.com               #",
    // The commented-out tests below are from mostly basic(), as things
    // used to work, the uncommented tests are how PG sees things.
    "example                          # Jo Jane; Ka Keller; Lea Long",
    "email=example                  # Jo Jane; Ka Keller; Lea Long",
    "email=ka@example*              # Ka Keller",
    "email=ka@example.*             # Ka Keller",
    "email=example.com              # Jo Jane; Ka Keller; Lea Long",
    "email=\"example com\"          # Jo Jane; Ka Keller; Lea Long",
    //"email=\"example com\"            #",
    "email<>example.com             # Jo Jane; Ka Keller; Lea Long",
    "email==ka@example.com          # Ka Keller",
    "name == \"Lea Long\"           # Lea Long",
    "name <> \"Lea Long\"           # Jo Jane; Ka Keller",
    "name = \"Lea *\"               # Lea Long", // Loose '*' should be ignored
    "name = \"*\"                   # Jo Jane; Ka Keller; Lea Long", // special case
    "name = \"Le* Lo*\"             # Lea Long",
    "name = \" * * \"               # Jo Jane; Ka Keller; Lea Long"
})
  public void basicFT(String testcase)
    throws IOException, FieldException, SchemaException, ServerChoiceIndexesException {
    logger.fine("basicFT: " + testcase);
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON("users.user_data", Arrays.asList("name", "email"));
    select(aCql2pgJson, testcase);
    logger.fine("basicFT: " + testcase + " OK ");
  }

  @Test
  @Parameters({
    // email works different, no need to test here. See basicFT()
    "name all \"\"                         # Jo Jane; Ka Keller; Lea Long",
    "name all Lea                          # Lea Long",
    "name all Long                         # Lea Long",
    "name all \"Lea Long\"                 # Lea Long",
    "name all \"Long Lea\"                 # Lea Long",
    "name all \"FooBar\"                   #",
    "name any \"\"                         # Jo Jane; Ka Keller; Lea Long",
    "name any Lea                          # Lea Long",
    "name any Long                         # Lea Long",
    "name any \"Lea Long\"                 # Lea Long",
    "name any \"Long Lea\"                 # Lea Long",
    "name any \"Lea FooBar\"               # Lea Long",
    "name any \"FooBar Long\"              # Lea Long",
    "name adj \"\"                         # Jo Jane; Ka Keller; Lea Long",
    "name adj Lea                          # Lea Long",
    "name adj Long                         # Lea Long",
    "name adj \"Lea Long\"                 # Lea Long",
    "name adj \"Long Lea\"                 #",})
  public void allAnyAdjFT(String testcase)
    throws IOException, FieldException, SchemaException, ServerChoiceIndexesException {
    logger.fine("allFT: " + testcase);
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON("users.user_data", Arrays.asList("name", "email"));
    select(aCql2pgJson, testcase);
    logger.fine("allFT: " + testcase + " OK ");
  }


  @Test
  @Parameters({
    /*
    "foobar=x                              #",
    "email all/respectCase example.com     # Jo Jane; Ka Keller; Lea Long",
    "email all/respectCase Example.com     #",
    "email all/respectAccents Example.com  # Jo Jane; Ka Keller; Lea Long",
    "email all/respectAccents Exämple.com  #",
    "name=Long                             # Lea Long",
     */
    "name=/RESpectCasE Long                # Unsupported modifier respectcase",
    "name=/ignoreCase Long                 # Unsupported modifier ignorecase",
    "name=/respectAccents Long             # Unsupported modifier respectaccents",
    "name=/ignoreAccents Long              # Unsupported modifier ignoreaccents"
  })
  public void unsupportedFT(String testcase) throws IOException, CQL2PgJSONException {
    logger.fine("unsupportedFT():" + testcase);
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(
      "users.user_data", Arrays.asList("name"));
    select(aCql2pgJson, testcase);
    logger.fine("unsupportedFT(): " + testcase + " OK");
  }

  @Test
  @Parameters({
    "lang = [] # a",
    "lang == [\"en\"]               # b",
    "lang = [\"en\"]               # b", //
  //  "lang = [\"au\"]               # i", //
  //
  /*    "                     lang ==/respectAccents []     # a",
    "cql.allRecords=1 NOT lang <>/respectAccents []     # a; n",
    "lang =/respectCase/respectAccents en               # b; c; d; f; g; h; i",

    // note that \"en\" also matches case f ["\"en"]
    "                     lang =/respectCase/respectAccents \\\"en\\\"   # b; f; i",  // without Java quoting: \"en\"
    "cql.allRecords=1 NOT lang =/respectCase/respectAccents \\\"en\\\"   # a; c; d; e; g; h; n",
    "lang = \"\"      NOT lang =/respectCase/respectAccents \\\"en\\\"   # a; c; d; e; g; h",
    "lang = \"\"                                                         # a; b; c; d; e; f; g; h; i",
    "cql.allRecords=1 NOT lang = \"\"                                    # n",
   */})
  public void arrayFT(String testcase) throws IOException, CQL2PgJSONException {
    logger.fine("arrayFT():" + testcase);
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(
      "users.user_data", Arrays.asList("name"));
    select(aCql2pgJson, "array.sql", testcase);
    logger.fine("arrayFT(): " + testcase + " OK");
  }

  /* Need to sort out the array stuff first
  @Test
  @Parameters({
    "lang==en   sortBy name                         # Jo Jane; Ka Keller; Lea Long",
    "example   sortBy name/sort.ascending          # Jo Jane; Ka Keller; Lea Long",
    "example   sortBy name/sort.descending         # Lea Long; Ka Keller; Jo Jane",
    "example   sortBy address.zip                  # Ka Keller; Jo Jane; Lea Long",
    "name==*a* sortBy name                         # Jo Jane; Ka Keller; Lea Long",
    "name==*a* sortBy name/sort.ascending          # Jo Jane; Ka Keller; Lea Long",
    "name==*a* sortBy name/sort.descending         # Lea Long; Ka Keller; Jo Jane",
    "name==*a* sortBy address.zip                  # Ka Keller; Jo Jane; Lea Long",
   })
  public void sortFT(String testcase) throws IOException, CQL2PgJSONException {
    logger.fine("sortFT():" + testcase);
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON(
      "users.user_data", dbSchema, Arrays.asList("name"));
    select(aCql2pgJson, testcase);
    logger.fine("sortFT(): " + testcase + " OK");
    select(testcase);
  }
*/
  /* And/Or/Not tests need to be done with different data, with pgs
  simple truncations. TODO.
   */
  //
 /* Simple subqueries, on the users and groups. Users point to groups.
  * Subqueries not enabled (yet)
   */
  //@Test
  @Parameters({
    "name = Long           # Lea Long",
    "groups.name = first   # Ka Keller "
  })
  public void subFT(String testcase) throws IOException, CQL2PgJSONException {
    logger.fine("subFT():" + testcase);
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON("users.user_data");
    select(aCql2pgJson, testcase);
    logger.fine("subFT(): " + testcase + " OK");
  }

  /* More complex subqueries, on instances, holdings, and items.
   * Note, we query on instances, locations point to instances, items point
   * to locations.
   * TODO: The subquery stuff is not really enabled yet, except incidentally
   * inside fulltext indexes.
   */
  //@Test
  @Parameters({
    "name = first          # first",
    "holdings.callNumber = 10  # noloc",
    "holdings.permLoc = HP    # all; holocp; holoctp",
    "holdings.tempLoc = HT    # all; holoct; holoctp",
    "holdings.tempLoc = HT or holdings.permLoc = HP # all; holocp; holoct; holoctp",
    "name <> first            # all; holocp; holoct; holoctp; itlocp; itloct; last; noloc",
    "name <> first not name = last  # all; holocp; holoct; holoctp; itlocp; itloct; noloc",
    "holdings.tempLoc = HT not holdings.permLoc = HP # holoct",
    "cql.allRecords=1  not name = first  # all; holocp; holoct; holoctp; itlocp; itloct; last; noloc",
    "holdings.tempLoc = \"\"  # all; holoct; holoctp",
    "cql.allRecords=1  not holdings.tempLoc=\"\"  # first; holocp; itlocp; itloct; last; noloc",
    "holdings.permLoc = HP NOT holdings.tempLoc=\"\" # holocp",
    "(holdings.tempLoc = HT) or (holdings.permLoc = HP NOT holdings.tempLoc=\"\") "
    + "# all; holocp; holoct; holoctp",
    "holdings.items.permLoc=IP # all; itlocp",
    "holdings.items.tempLoc=IT # all; itloct",
    "holdings.items.permLoc = IP NOT holdings.items.tempLoc=\"\" # itlocp",
    "(holdings.items.tempLoc = IT) or (holdings.items.permLoc = IP NOT holdings.items.tempLoc=\"\") "
    + "# all; itlocp; itloct",
    // test with two holdings records, one with good permloc ZZ, one where a temp YY overrides it.
    // We should still get the instance 'all', but the NOT clause kills both holdings.
    // "(holdings.tempLoc = ZZ) or (holdings.permLoc = ZZ NOT holdings.tempLoc=\"\") # all",
    //
    // Experiments to see if I can rewrite the query
    "holdings.tempLoc = ( ZZ OR YY )# all", // expands internally to loc=ZZ or loc=YY
    "holdings.tempLoc = AA AND holdings.permLoc = ZZ # all", // expands internally to loc=ZZ or loc=YY
    //
    // Building up to the effective location test with XX
    "holdings.tempLoc = XX # holoctp", //
    "holdings.permLoc = XX NOT holdings.tempLoc=\"\" # holocp ",
    "(holdings.tempLoc = XX) or (holdings.permLoc = XX NOT holdings.tempLoc=\"\") "
    + "# holocp; holoctp",
    "holdings.items.tempLoc = XX # itloct", //
    "holdings.items.permLoc = XX NOT holdings.items.tempLoc=\"\" # itlocp ",
    "(holdings.items.tempLoc = XX) or (holdings.items.permLoc = XX NOT holdings.items.tempLoc=\"\") "
    + "# itlocp; itloct",
 // towards the final effective location clause
  /*
    "(holdings.items.tempLoc = XX) "
    + "OR (holdings.items.permLoc = XX NOT holdings.items.tempLoc=\"\") "
    + "OR (holdings.temploc = XX NOT holdings.items.tempLoc =\"\" NOT holdings.items.permLoc = \"\" )"
    + " # holoctp; itlocp; itloct "
    // This fails, the items clause masks away also the holdings temploc, althoug it is for a
    // different item.
   */ //
  })
  public void instanceSubFT(String testcase) throws IOException, CQL2PgJSONException {
    logger.fine("instanceSubFT():" + testcase);
    CQL2PgJSON aCql2pgJson = new CQL2PgJSON("instances.jsonb");
    select(aCql2pgJson, "instances.sql", testcase);
    logger.fine("instanceSubFT(): " + testcase + " OK");
  }

}
