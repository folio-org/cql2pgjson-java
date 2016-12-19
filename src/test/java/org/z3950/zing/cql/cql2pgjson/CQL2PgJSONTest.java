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

import junitparams.FileParameters;
import junitparams.JUnitParamsRunner;

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

    @Test
    @FileParameters("classpath:users.csv")
    public void select(String cql, String expectedNames) {
        // the "## " better visually separates the fields for humans editing the csv file.
        assertTrue("expectedNames starts with ##", expectedNames.startsWith("##"));
        String expectedNames2 = expectedNames.substring(2).trim();

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
            assertEquals("CQL: " + cql + ", SQL: " + where, expectedNames2, actualNames);
        } catch (SQLException e) {
            throw new RuntimeException(sql, e);
        }
    }
}
