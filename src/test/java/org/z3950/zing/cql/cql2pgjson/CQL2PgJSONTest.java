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

    @Test
    @Parameters({
        "name=Long                      # Lea Long",
        "name=Lea or name=Keller        # Ka Keller; Lea Long",
        "email=jo or name=\"Ka Keller\" # Jo Jane; Ka Keller",
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
        })
    public void basic(String testcase) {
        select(testcase);
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
        })
    public void wildcards(String testcase) {
        select(testcase);
    }

    @Test
    @Parameters({
        // check correct masking of special chars
        "'        OR Jo                 # Jo Jane",
        "\\       OR Jo                 # Jo Jane",
        "name=='  OR Jo                 # Jo Jane",
        "name==\\ OR Jo                 # Jo Jane",
        })
    public void special(String testcase) {
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
}
