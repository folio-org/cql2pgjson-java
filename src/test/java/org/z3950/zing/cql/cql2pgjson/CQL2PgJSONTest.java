package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig;

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
        cql2pgJson = new CQL2PgJSON("users.user_data", Util.getResource("userdata.json"));
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

    private void select(String cql, String where, String expectedNames) {
        String sql = "select user_data->'name' from users where " + where + " order by user_data->'name'";
        try {
            Statement statement = conn.createStatement();
            statement.execute(sql);
            ResultSet result = statement.getResultSet();
            String actualNames = "";
            while (result.next()) {
                if (! "".equals(actualNames)) {
                    actualNames += ", ";
                }
                actualNames += result.getString(1).toString().replace("\"", "");
            }
            assertEquals("CQL: " + cql + ", SQL: " + where, expectedNames, actualNames);
        } catch (SQLException e) {
            throw new RuntimeException(sql, e);
        }
    }

    private void test(String testFile) {
        String tests [] = Util.getResource("users.test").split("[\\n\\r]+");
        for (String test : tests) {
            String s [] = test.split("## ");
            String cql = s[0];
            String expectedNames = s[1];
            String sql = cql2pgJson.cql2pgJson(cql);
            select(cql, sql, expectedNames);
        }
    }

    @Test
    public void users() {
        test("users.test");
    }
}
