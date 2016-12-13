package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.z3950.zing.cql.CQLBoolean;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CQL2PgJSON {
    /**
     * Contextual Query Language (CQL) Specification: https://www.loc.gov/standards/sru/cql/spec.html
     */

    /** default index names */
    private static List<String> serverChoiceFields = Arrays.asList("a", "b");

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

    private static String toSql(CQLBoolean bool) {
        switch (bool) {
        case AND: return "AND";
        case OR:  return "OR";
        case NOT: return "AND NOT";
        default: throw new IllegalArgumentException("Not implemented yet: " + bool);
        }
    }

    private static String pg(CQLBooleanNode node) {
        return "(" + pg(node.getLeftOperand()) + ") "
            + toSql(node.getOperator())
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
        String match = "='" + maskSingleQuotes(node.getTerm()) + "'";
        if (node.getIndex().equals("cql.serverChoice")) {
            return serverChoiceFields.stream().map(f -> f + match).collect(Collectors.joining(" OR "));
        } else {
            return node.getIndex() + match;
        }
    }
}
