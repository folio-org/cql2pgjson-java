# cql2pgjson-java
CQL (Contextual Query Language) to PostgreSQL JSON converter in Java.

Invoke like this:

    // users.user_data is a JSONB field in the users table.
    CQL2PgJSON cql2pgJson = new CQL2PgJSON("users.user_data");
    String cql = "name=Miller";
    String where = cql2pgJson.cql2pgJson(cql);
    String sql = "select * from users where " + where;
    // select * from users where CAST(users.user_data->'name' AS text) LIKE '%Miller%'

Setting server choice indexes is possible:

    CQL2PgJSON cql2pgJson = new CQL2PgJSON("users.user_data", Arrays.asList("name", "email"));
    String cql = "Miller";
    String where = cql2pgJson.cql2pgJson(cql);
    String sql = "select * from users where " + where;
    // select * from users where CAST(users.user_data->'name' AS text) LIKE '%Miller%'
    //                        OR CAST(users.user_data->'email' AS text) LIKE '%Miller%'

Only these relations have been implemented yet:

* = (substring match)
* == (exact match)
