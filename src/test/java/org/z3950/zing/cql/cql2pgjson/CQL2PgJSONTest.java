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

    private static void setupData(String sqlFile) throws SQLException {
        String sql = Util.getResource(sqlFile);
        // split at semicolon at end of line (removing optional whitespace)
        String statements [] = sql.split(";\\s*[\\n\\r]\\s*");
        for (String stmt : statements) {
            conn.createStatement().execute(stmt);
        }
    }

    @BeforeClass
    public static void runOnceBeforeClass() throws IOException, SQLException {
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

    public void select(String testcase) {
        int hash = testcase.indexOf('#');
        assertTrue("hash character in testcase", hash>=0);
        String cql           = testcase.substring(0, hash).trim();
        String expectedNames = testcase.substring(hash+1).trim();

        if (! cql.contains(" sortBy ")) {
            cql += " sortBy name";
        }
        String where = cql2pgJson.cql2pgJson(cql);
        String sql = "select user_data->'name' from users where " + where;
        try {
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
                        e.getMessage().contains(s));
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
        "name == \"Lea Long\"           # Lea Long",
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
        // check correct masking of special chars
        "'        OR Jo                 # Jo Jane",
        "\\       OR Jo                 # Jo Jane",
        "name=='  OR Jo                 # Jo Jane",
        "name==\\ OR Jo                 # Jo Jane",
        "address.city=\\*               # Jo Jane",
        "address.city=\\?               # Ka Keller",
        "address.city=\\\\              # Lea Long",
        "address.city=\\                # Lea Long",
        "\\K\\a                         # Ka Keller",
        })
    public void special(String testcase) {
        select(testcase);
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
        "address.city=Søvang            # Lea Long",
        "address.city=øvang             #",
        "address.city=vang              #",
        "address.city=S?vang            # Lea Long",
        "address.city=S*vang            # Lea Long",
        "address.city=*ang              # Lea Long",
        "address.city=SØvang            # Lea Long",
        "address.city=Sövang            # Lea Long",
        "address.city=SÖvang            # Lea Long",
        "address.city=Sävang            #",
        "address.city=SÄvang            #",
        })
    public void unicode(String testcase) {
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

    @Test(expected = IllegalArgumentException.class)
    public void nullField() {
        new CQL2PgJSON(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyField() {
        new CQL2PgJSON(" \t \t ");
    }

    @Test
    public void noServerChoiceIndexes() throws IOException {
        cql2pgJsonException(new CQL2PgJSON("users.user_data", Arrays.asList()),
                "Jane", IllegalStateException.class, "serverChoiceIndex");
        cql2pgJsonException(new CQL2PgJSON("users.user_data", (List<String>) null),
                "Jane", IllegalStateException.class, "serverChoiceIndex");
    }

    @Test
    public void relationNotImplemented() {
        cql2pgJsonException(new CQL2PgJSON("users.user_data"),
                "name > Jane", IllegalArgumentException.class, "Relation", ">");
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
