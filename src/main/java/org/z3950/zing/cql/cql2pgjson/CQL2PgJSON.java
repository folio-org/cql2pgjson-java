package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNotNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CQL2PgJSON {
    /*
     * Contextual Query Language (CQL) Specification:
     * https://www.loc.gov/standards/sru/cql/spec.html
     * https://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part5-cql/searchRetrieve-v1.0-os-part5-cql.html
     */

    /**
     * Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
     * Must conform to SQL identifier requirements (characters, not a keyword), or properly
     * quoted using double quotes.
     */
    private String jsonField;
    /** JSON schema of jsonb field as object tree */
    private Object schema;

    /**
     * Default index names to be used for cql.serverChoice.
     * May be empty, but not null. Must not contain null, names must not contain double quote or single quote.
     */
    private List<String> serverChoiceIndexes = Collections.emptyList();

    private enum CqlMasking {
      MASKED, UNMASKED, SUBSTRING, REGEXP;
      @Override
      public String toString() {
        return super.toString().toLowerCase();
      }
    }

    /** includes unicode characters */
    private static final String WORD_CHARACTER_REGEXP = "[^[:punct:][:space:]]";

    /**
     * Create an instance for the specified schema.
     *
     * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
     *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
     *   quoted using double quotes.
     */
    public CQL2PgJSON(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException("field (containing tableName) must not be empty");
        }
        this.jsonField = field;
    }

    /**
     * Create an instance for the specified schema.
     *
     * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
     *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
     *   quoted using double quotes.
     * @param schema JSON String representing the schema of the field the CQL queries against.
     * @throws IOException if the JSON structure is invalid
     */
    public CQL2PgJSON(String field, String schema) throws IOException {
        this(field);
        setSchema(schema);
    }

    /**
     * Create an instance for the specified schema.
     *
     * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
     *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
     *   quoted using double quotes.
     * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
     *                                  names must not contain double quote or single quote.
     * @throws IOException if the JSON structure is invalid
     */
    public CQL2PgJSON(String field, List<String> serverChoiceIndexes) throws IOException {
        this(field);
        setServerChoiceIndexes(serverChoiceIndexes);
    }

    /**
     * Create an instance for the specified schema.
     *
     * @param field Name of the JSON field, may include schema and table name (e.g. tenant1.user_table.json).
     *   Must conform to SQL identifier requirements (characters, not a keyword), or properly
     *   quoted using double quotes.
     * @param schema JSON String representing the schema of the field the CQL queries against.
     * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
     *                                  names must not contain double quote or single quote.
     * @throws IOException if the JSON structure is invalid
     */
    public CQL2PgJSON(String field, String schema, List<String> serverChoiceIndexes) throws IOException {
        this(field);
        setSchema(schema);
        setServerChoiceIndexes(serverChoiceIndexes);
    }

    /**
     * Set the schema of the field.
     * @param schema  JSON String representing the schema of the field the CQL queries against.
     * @throws IOException if the JSON structure is invalid
     */
    private void setSchema(String schema) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.schema = mapper.readValue(schema, Object.class);
    }

    /**
     * Set the index names (field names) for cql.serverChoice.
     * @param serverChoiceIndexes       List of field names, may be empty, must not contain null,
     *                                  names must not contain double quote or single quote.
     */
    public void setServerChoiceIndexes(List<String> serverChoiceIndexes) {
        if (serverChoiceIndexes == null) {
            this.serverChoiceIndexes = Collections.emptyList();
            return;
        }
        for (String field : serverChoiceIndexes) {
            if (field == null) {
                throw new NullPointerException("serverChoiceFields must not contain null elements");
            }
            if (field.trim().isEmpty()) {
                throw new IllegalArgumentException("serverChoiceFields must not contain emtpy field names");
            }
            int pos = field.indexOf('"');
            if (pos >= 0) {
                throw new IllegalArgumentException("field contains double quote at position " + pos+1 + ": " + field);
            }
            pos = field.indexOf('\'');
            if (pos >= 0) {
                throw new IllegalArgumentException("field contains single quote at position " + pos+1 + ": " + field);
            }
        }
        this.serverChoiceIndexes = serverChoiceIndexes;
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
        if (node instanceof CQLSortNode) {
            return pg((CQLSortNode) node);
        }
        throw new IllegalArgumentException("Not implemented yet: " + node.getClass().getName());
    }

    private String pg(CQLSortNode node) {
        StringBuilder order = new StringBuilder();
        order.append(pg(node.getSubtree()))
             .append(" ORDER BY ");
        boolean firstIndex = true;
        for (ModifierSet modifierSet : node.getSortIndexes()) {
            if (firstIndex) {
                firstIndex = false;
            } else {
                order.append(", ");
            }
            String index = modifierSet.getBase();
            order.append(jsonField).append("->'").append(index.replace(".", "'->'")).append("'");

            String sort = null;
            // find last sort modifier
            for (Modifier modifier : modifierSet.getModifiers()) {
                switch (modifier.getType()) {
                case "sort.ascending":
                    sort = " ASC";
                    break;
                case "sort.descending":
                    sort = " DESC";
                    break;
                default:
                    // ignore
                }
            }
            if (sort != null) {
                order.append(sort);
            }
        }
        return order.toString();
    }

    private String sqlOperator(CQLBooleanNode node) {
        if (node instanceof CQLAndNode) {
            return "AND";
        }
        if (node instanceof CQLOrNode) {
            return "OR";
        }
        if (node instanceof CQLNotNode) {
            // CQL "NOT" means SQL "AND NOT", see section "7. Boolean Operators" in
            // https://www.loc.gov/standards/sru/cql/spec.html
            return "AND NOT";
        }
        throw new IllegalArgumentException("Not implemented yet: " + node.getClass().getName());
    }

    private String pg(CQLBooleanNode node) {
        return "(" + pg(node.getLeftOperand()) + ") "
            + sqlOperator(node)
            + " (" + pg(node.getRightOperand()) + ")";
    }

  /**
   * Return the last CqlMasking contained in modifiers. If none use MASKED as default.
   * @param modifiers where to search in
   * @return one of CqlMasking.
   */
  private static CqlMasking masking(List<Modifier> modifiers) {
    CqlMasking masking = CqlMasking.MASKED;  // default
    for (Modifier m : modifiers) {
      switch (m.getType().toLowerCase()) {
      case "masked":
        masking = CqlMasking.MASKED;
        break;
      case "unmasked":
        masking = CqlMasking.UNMASKED;
        break;
      case "substring":
        masking = CqlMasking.SUBSTRING;
        break;
      case "regexp":
        masking = CqlMasking.REGEXP;
        break;
      default:
        // ignore
      }
    }
    return masking;
  }

  private static String regexp(String s) {
    StringBuilder regexp = new StringBuilder();
    boolean backslash = false;
    for (char c : s.toCharArray()) {
      if (backslash) {
        // Backslash (\) is used to escape '*', '?', quote (") and '^' , as well as itself.
        // Backslash followed by any other characters is an error (see cql spec), but
        // we handle it gracefully matching that character.
        regexp.append(Unicode.IGNORE_CASE_AND_DIACRITICS.getEquivalents(c));
        backslash = false;
        continue;
      }
      switch (c) {
      case '\\':
        backslash = true;
        break;
      case '?':
        regexp.append(WORD_CHARACTER_REGEXP);
        break;
      case '*':
        regexp.append(WORD_CHARACTER_REGEXP + "*");
        break;
      case '^':
        regexp.append("(^\"?|\"?$)");
        break;
      default:
        regexp.append(Unicode.IGNORE_CASE_AND_DIACRITICS.getEquivalents(c));
      }
    }

    if (backslash) {
      // a single backslash at the end is an error but we handle it gracefully matching one.
      regexp.append(Unicode.IGNORE_CASE_AND_DIACRITICS.getEquivalents('\\'));
    }

    // mask ' used for quoting postgres strings
    return regexp.toString().replace("'", "''");
  }

    /**
     * Return a POSIX regexp expression for the cql expression.
     * @param cql   expression to convert
     * @return resulting regexp
     */
    private static String [] cql2regexp(String cql) {
        String [] split = cql.split("\\s+");  // split at whitespace
        if (split.length == 0) {
            // cql contains whitespace only.
            // honorWhitespace is not implemented yet,
            // whitespace only results in empty string and matches anything
            return new String [] { " ~ ''" };
        }
        for (int i=0; i<split.length; i++) {
            split[i] = " ~ '(^|[[:punct:]]|[[:space:]])"
                    + regexp(split[i])
                    + "($|[[:punct:]]|[[:space:]])'";

        }
        return split;
    }

    private String [] match(CQLTermNode node) {
        CqlMasking masking = masking(node.getRelation().getModifiers());
        if (! masking.equals(CqlMasking.MASKED)) {
            throw new IllegalArgumentException("This masking is not implemented yet: " + masking);
        }
        switch (node.getRelation().getBase()) {
        case "==":
            // accept quotes at beginning and end because JSON string are
            // quoted (but JSON numbers aren't)
            return new String [] { " ~* '^\"?" + regexp(node.getTerm()) + "\"?$'" };
        case "=":
            return cql2regexp(node.getTerm());
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
        return "CAST(" + jsonField + "->'" + index.replace(".", "'->'") + "' AS text)";
    }

    /**
     * Create an SQL expression where index is applied to all matches.
     * @param index   index to use
     * @param matches  list of match expressions
     * @return SQL expression
     */
    private String index2sql(String index, String [] matches) {
        StringBuilder s = new StringBuilder();
        for (String match : matches) {
            if (s.length() > 0) {
                s.append(" AND ");
            }
            s.append(index2sql(index) + match);
        }
        return s.toString();
    }

    private String pg(CQLTermNode node) {
        String [] matches = match(node);
        if ("cql.serverChoice".equalsIgnoreCase(node.getIndex())) {
            if (serverChoiceIndexes.isEmpty()) {
                throw new IllegalStateException("cql.serverChoice requested, but not serverChoiceIndexes defined.");
            }
            return serverChoiceIndexes.stream()
                    .map(index -> index2sql(index, matches))
                    .collect(Collectors.joining(" OR "));
        } else {
            return index2sql(node.getIndex(), matches);
        }
    }
}
