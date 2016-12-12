package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;

import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CQL2PgJSON {
    private CQL2PgJSON() throws InstantiationException {
        throw new InstantiationException("Utility class");
    }

    public static String cql2pgJson(String schema, String cql) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object tree = mapper.readValue(schema, Object.class);

            CQLParser parser = new CQLParser();
            CQLNode node = parser.parse(cql);
            return pg(node);
        } catch (IOException|CQLParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String pg(CQLNode node) {
        if (node instanceof CQLTermNode) {
            return pg((CQLTermNode) node);
        }
        if (node instanceof CQLBooleanNode) {
            return pg((CQLBooleanNode) node);
        }
        throw new IllegalArgumentException("Not implemented yet: " + node.getClass().getName());
    }

    private static String pg(CQLBooleanNode node) {
        switch (node.getOperator()) {
        case NOT: return "! (" + pg(node.getLeftOperand()) + ")";
        default: break;
        }
        return "(" + pg(node.getLeftOperand()) + ") "
            + node.getOperator()
            + " (" + pg(node.getRightOperand()) + ")";
    }

    /**
     * Replace each single quote by two single quotes. Required for SQL string constants:
     * https://www.postgresql.org/docs/9.6/static/sql-syntax-lexical.html#SQL-SYNTAX-CONSTANTS
     * @param s
     * @return String with single quotes masked
     */
    private static String maskSingleQuotes(String s) {
        return s.replace("'", "''");
    }

    private static String pg(CQLTermNode node) {
        return node.getIndex() + "='" + maskSingleQuotes(node.getTerm()) + "'";
    }
}
