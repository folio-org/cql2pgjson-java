package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.*;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig;

@RunWith(JUnitParamsRunner.class)
public class CQL2PgJSONTest {
  static PostgresProcess postgresProcess;
  static Connection conn;
  static final String dbName = "test";
  static final String username = "test";
  static final String password = "test";
  static CQL2PgJSON cql2pgJson;

  private static void setupDatabase() throws IOException, SQLException {
    // TODO: take values from dbtest_connection command line variable
    // try local Postgres on Port 5432 with user test
    String url = "jdbc:postgresql://127.0.0.1:5432/test?currentSchema=public&user=test&password=test";
    try {
      conn = DriverManager.getConnection(url);
      return;
    }
    catch (SQLException e) {
      // ignore and start embedded Postgres
    }

    // start embedded Postgres
    final PostgresStarter<PostgresExecutable, PostgresProcess> runtime = PostgresStarter.getDefaultInstance();
    final PostgresConfig config = PostgresConfig.defaultWithDbName(dbName, username, password);
    url = String.format("jdbc:postgresql://%s:%s/%s?currentSchema=public&user=%s&password=%s",
        config.net().host(),
        config.net().port(),
        config.storage().dbName(),
        config.credentials().username(),
        config.credentials().password()
        );
    PostgresExecutable exec = runtime.prepare(config);
    postgresProcess = exec.start();
    conn = DriverManager.getConnection(url);
  }

  private static void setupData(String sqlFile) {
    String sql = Util.getResource(sqlFile);
    // split at semicolon at end of line (removing optional whitespace)
    String statements [] = sql.split(";\\s*[\\n\\r]\\s*");
    for (String stmt : statements) {
      try {
        conn.createStatement().execute(stmt);
      } catch (SQLException e) {
        throw new RuntimeException(stmt, e);
      }
    }
  }

  @BeforeClass
  public static void runOnceBeforeClass() throws IOException, SQLException, SchemaException {
    setupDatabase();
    setupData("users.sql");
    cql2pgJson = new CQL2PgJSON("users.user_data", Util.getResource("userdata.json"), Arrays.asList("name", "email"));
  }

  @AfterClass
  public static void runOnceAfterClass() throws SQLException {
    if (conn != null) {
      conn.close();
    }
    if (postgresProcess != null) {
      postgresProcess.stop();
    }
  }

  public void select(String sqlFile, String testcase) {
    int hash = testcase.indexOf('#');
    assertTrue("hash character in testcase", hash>=0);
    String cql           = testcase.substring(0, hash).trim();
    String expectedNames = testcase.substring(hash+1).trim();

    if (! cql.contains(" sortBy ")) {
      cql += " sortBy name";
    }
    String where = null;
    try {
      where = cql2pgJson.cql2pgJson(cql);
    } catch (QueryValidationException e1) {
      e1.printStackTrace();
      fail(e1.getMessage());
    }
    String sql = "select user_data->'name' from users where " + where;
    try {
      setupData(sqlFile);
      Statement statement = conn.createStatement();
      statement.execute(sql);
      ResultSet result = statement.getResultSet();
      String actualNames = "";
      while (result.next()) {
        if (! "".equals(actualNames)) {
          actualNames += "; ";
        }
        actualNames += result.getString(1).replace("\"", "");
      }
      assertEquals("CQL: " + cql + ", SQL: " + where, expectedNames, actualNames);
    } catch (SQLException e) {
      throw new RuntimeException(sql, e);
    }
  }

  public void select(String testcase) {
    select("jo-ka-lea.sql", testcase);
  }

  /**
   * Invoke CQL2PgJSON.cql2pgJson(cql) expecting an exception.
   * @param cql  the cql expression that should trigger the exception
   * @param clazz  the expected class of the exception
   * @param contains  the expected strings of the exception message
   * @throws RuntimeException  if an exception was thrown that is not an instance of clazz
   */
  public void cql2pgJsonException(String cql,
      Class<? extends Exception> clazz, String ... contains) {
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
      Class<? extends Exception> clazz, String ... contains) {
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
    "email==example.com             #",
    "email<>example.com             # Jo Jane; Ka Keller; Lea Long",
    "name == \"Lea Long\"           # Lea Long",
    "name <> \"Lea Long\"           # Jo Jane; Ka Keller",
    // whitespace is removed, empty string matches anything (including email without whitespace)
    "email=\" \"                    # Jo Jane; Ka Keller; Lea Long",
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

  @Test
  public void prox() {
    cql2pgJsonException("name=Lea prox/unit=word/distance>3 name=Long",
        IllegalArgumentException.class, "CQLProxNode");
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
    cql2pgJsonException("name=/unmasked Lea",  IllegalArgumentException.class, "unmasked");
    cql2pgJsonException("name=/substring Lea", IllegalArgumentException.class, "substring");
    cql2pgJsonException("name=/regexp Lea",    IllegalArgumentException.class, "regexp");
  }

  @Test
  @Parameters({
    "email==\\\\                    # a",
    "email==\\\\\\\\                # b",
    "email==\\*                     # c",
    "email==\\*\\*                  # d",
    "email==\\?                     # e",
    "email==\\?\\?                  # f",
    "email==\\\"                    # g",
    "email==\\\"\\\"                # h",
    "             address.zip=1     # a",
    "'         OR address.zip=1     # a",
    "name=='   OR address.zip=1     # a",
    "name==\\  OR address.zip=1     # a",
    "\\a                            # a",
  })
  public void special(String testcase) {
    select("special.sql", testcase);
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

  @Test
  @Parameters({
    "address.city= /respectAccents Søvang # Lea Long",
    "address.city==/respectAccents Søvang # Lea Long",
    "address.city= /respectAccents SØvang # Lea Long",
    "address.city==/respectAccents SØvang # Lea Long",
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
    "example sortBy name                         # Jo Jane; Ka Keller; Lea Long",
    "example sortBy name/sort.ascending          # Jo Jane; Ka Keller; Lea Long",
    "example sortBy name/sort.descending         # Lea Long; Ka Keller; Jo Jane",
    "example sortBy notExistingIndex address.zip # Ka Keller; Jo Jane; Lea Long",
  })
  public void sort(String testcase) {
    select(testcase);
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
    "address.zip<>5                 # a; b; c; d; f; g; h",
  })
  public void compareNumber(String testcase) {
    select("special.sql", testcase);
  }

  @Test
  public void compareNumberNotImplemented() {
    cql2pgJsonException(new CQL2PgJSON("users.user_data"), "address.zip adj 5",
        IllegalArgumentException.class, "Relation", "adj");
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

  @Test(expected = IllegalArgumentException.class)
  public void nullField() {
    new CQL2PgJSON(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyField() {
    new CQL2PgJSON(" \t \t ");
  }

  @Test
  public void noServerChoiceIndexes() throws IOException, SchemaException {
    cql2pgJsonException(new CQL2PgJSON("users.user_data", Arrays.asList()),
        "Jane", IllegalStateException.class, "serverChoiceIndex");
    cql2pgJsonException(new CQL2PgJSON("users.user_data", (List<String>) null),
        "Jane", IllegalStateException.class, "serverChoiceIndex");
    cql2pgJsonException(new CQL2PgJSON("users.user_data", "{}"),
        "Jane", IllegalStateException.class, "serverChoiceIndex");
  }

  @Test
  public void relationNotImplemented() {
    cql2pgJsonException(new CQL2PgJSON("users.user_data"),
        "address.zip encloses 12", IllegalArgumentException.class, "Relation", "encloses");
  }

  @Test(expected = NullPointerException.class)
  public void nullIndex() throws IOException {
    new CQL2PgJSON("users.user_data", Arrays.asList((String) null));
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyIndex() throws IOException {
    new CQL2PgJSON("users.user_data", Arrays.asList(" \t \t "));
  }

  @Test(expected = IllegalArgumentException.class)
  public void doubleQuoteIndex() throws IOException {
    new CQL2PgJSON("users.user_data", Arrays.asList("test\"cql"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void singleQuoteIndex() throws IOException {
    new CQL2PgJSON("users.user_data", Arrays.asList("test'cql"));
  }
}
