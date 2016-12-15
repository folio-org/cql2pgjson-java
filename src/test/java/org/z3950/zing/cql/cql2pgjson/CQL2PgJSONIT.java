package org.z3950.zing.cql.cql2pgjson;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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

public class CQL2PgJSONIT {
    static PostgresProcess postgresProcess;
    static Connection conn;
    static final String dbName = "test";
    static final String username = "test";
    static final String password = "test";

    /**
     * Insert userDataJson into users table after converting single quote to double quote.
     *
     * Single quote makes JSON more readable in java code files.
     *
     * @param userDataJson      json to insert
     * @throws SQLException     on sql error
     */
    private static void insert(String userDataJson) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (user_data) VALUES (cast(? as jsonb))");
        stmt.setString(1, userDataJson.replace("'", "\""));
        assertEquals(1, stmt.executeUpdate());
    }

    private static void setupData() throws SQLException {
        conn.createStatement().execute("DROP TABLE IF EXISTS users");
        conn.createStatement().execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        conn.createStatement().execute("CREATE TABLE users (id UUID PRIMARY KEY DEFAULT uuid_generate_v1mc(), user_data JSONB NOT NULL);");
        insert("{'name': 'Jo Jane', 'email': 'jo@example.com', 'address': {'city': 'Sydhavn', 'zip': 2450}, 'lang': ['en', 'pl']}");
        insert("{'name': 'Ka Keller', 'email': 'ka@example.com', 'address': {'city': 'Fred', 'zip': 1900}, 'lang': ['en', 'dk', 'fi']}");
        insert("{'name': 'Lea Long', 'email': 'lea@example.com', 'address': {'city': 'Søvang', 'zip': 2791}, 'lang': ['en', 'dk']}");
    }

    @BeforeClass
    public static void runOnceBeforeClass() throws IOException, SQLException {
        // TODO: take values from dbtest_connection command line variable
        String url = "jdbc:postgresql://127.0.0.1:5432/test?currentSchema=public&user=test&password=test";
        try {
            conn = DriverManager.getConnection(url);
            return;
        }
        catch (SQLException e) {
            // ignore
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

        setupData();
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

    private void select(String where, String expectedNames) throws SQLException {
        final Statement statement = conn.createStatement();
        String sql = "select user_data->'name' from users where " + where + " order by user_data->'name'";
        try {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new SQLException(sql, e);
        }
        ResultSet result = statement.getResultSet();
        String actualNames = "";
        while (result.next()) {
            if (! "".equals(actualNames)) {
                actualNames += ", ";
            }
            actualNames += result.getString(1).replace("\"", "");
        }
        assertEquals(where, expectedNames, actualNames);
    }

    @Test
    public void testSelect() throws SQLException {
        select("true", "Jo Jane, Ka Keller, Lea Long");
        select("user_data @> '{\"lang\": [\"fi\", \"en\"] }'", "Ka Keller");
    }

    private void cql(String cql, String expectedNames) throws SQLException {
        String sql = CQL2PgJSON.cql2pgJson("{}", cql);
        select(sql, expectedNames);
    }

    @Test
    public void test() throws SQLException {
        cql("name='Lea Long'", "Lea Long");
    }
}
