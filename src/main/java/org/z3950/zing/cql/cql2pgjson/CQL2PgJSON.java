package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.z3950.zing.cql.CQLBoolean;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CQL2PgJSON {
    /*
     * Contextual Query Language (CQL) Specification: https://www.loc.gov/standards/sru/cql/spec.html
     */

    /**
     * Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
     * Must conform to SQL identifiert requirements (characters, not a keyword), or properly
     * quoted using double quotes.
     */
    private String field;
    /** JSON schema of jsonb field as object tree */
    private Object schema;

    /** default index names */
    private static List<String> serverChoiceFields = Arrays.asList("name", "email");

    /**
     * Create an instance for the specified schema.
     *
     * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
     *   Must conform to SQL identifiert requirements (characters, not a keyword), or properly
     *   quoted using double quotes.
     * @param schema JSON String representing the schema of the field the CQL queries against.
     * @throws IOException if the JSON structure is invalid
     */
    public CQL2PgJSON(String field, String schema) throws IOException {
        if (field == null || field.isEmpty() || field.equals(" ")) {
            throw new IllegalArgumentException("tableName must not be empty");
        }
        this.field = field;

        ObjectMapper mapper = new ObjectMapper();
        this.schema = mapper.readValue(schema, Object.class);
    }

    public String cql2pgJson(String cql) {
        try {
            CQLParser parser = new CQLParser();
            CQLNode node = parser.parse(cql);
            return pg(node);
        } catch (IOException|CQLParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String pg(CQLNode node) {
        if (node instanceof CQLTermNode) {
            return pg((CQLTermNode) node);
        }
        if (node instanceof CQLBooleanNode) {
            return pg((CQLBooleanNode) node);
        }
        throw new IllegalArgumentException("Not implemented yet: " + node.getClass().getName());
    }

    private String toSql(CQLBoolean bool) {
        switch (bool) {
        case AND: return "AND";
        case OR:  return "OR";
        case NOT: return "AND NOT";
        default: throw new IllegalArgumentException("Not implemented yet: " + bool);
        }
    }

    private String pg(CQLBooleanNode node) {
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

    /**
     * Mask these characters for LIKE: \ * % _ ? '
     * @param s
     * @return
     */
    private static String cql2like(String s) {
        return StringUtils.replaceEach(s,
                new String[]{"\\\\", "\\*", "%",   "*", "\\_", "\\?", "_"  , "?", "'"  },
                new String[]{"\\\\", "\\*", "\\%", "%", "\\_", "\\?", "\\_", "_", "''" } );
    }

    private String match(CQLTermNode node) {
        switch (node.getRelation().getBase()) {
        case "==":
            String term = maskSingleQuotes(node.getTerm());
            // JSON numbers don't have double quotes, JSON strings do have
            // term=foo: in ('foo', '"foo"')
            // term=1.5: in ('1.5', '"1.5"')
            return " in ('" + term + "', '\"" + term + "\"')";
        case "=":
            return " LIKE '%" + cql2like(node.getTerm()) + "%'";
        default:
            throw new IllegalArgumentException("Relation " + node.getRelation().getBase()
                    + " not implemented yet: " + node.toString());
        }
    }

    /**
     * Convert index name to SQL term.
     * Example result for field=user and index=foo.bar:
     * CAST(user->'foo'->'bar' AS text)
     *
     * @param index name to convert
     * @return SQL term
     */
    private String index2sql(String index) {
        return "CAST(" + field + "->'" + index.replace(".", "'->'") + "' AS text)";
    }

    private String pg(CQLTermNode node) {
        String match = match(node);
        if (node.getIndex().equals("cql.serverChoice")) {
            return serverChoiceFields.stream()
                    .map(f -> index2sql(f) + match)
                    .collect(Collectors.joining(" OR "));
        } else {
            return index2sql(node.getIndex()) + match;
        }
    }
}
