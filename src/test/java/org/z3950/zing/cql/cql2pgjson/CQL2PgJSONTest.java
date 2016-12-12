package org.z3950.zing.cql.cql2pgjson;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class CQL2PgJSONTest {
    static String schema = getResource("userdata.json");

    private static String getResource(String filePath) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            return IOUtils.toString(classLoader.getResource(filePath), UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String pg(String cql) {
        return CQL2PgJSON.cql2pgJson(schema, cql);
    }

    @Test
    public void quotes() {
        assertEquals("a=''''", pg("a='"));
        assertEquals("a='''x''y'''", pg("a='x'y'"));
    }

    @Test
    public void or() {
        assertEquals("(x.a='foo') OR (x.a='bar')", pg("x.a=(foo or bar)"));
    }

    @Test
    public void and() {
        assertEquals("(x.a='foo') AND (a='bar')", pg("x.a=foo and a=bar"));
    }

    @Test
    public void andOr() {
        assertEquals("((a='foo') AND (a='bar')) OR (a='baz')", pg("a=foo and a=bar or a=baz"));
    }

}
