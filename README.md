# cql2pgjson-java

CQL (Contextual Query Language) to PostgreSQL JSON converter in Java.

## License

Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Usage

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

Search is case insensitive, but only for ASCII characters, not for other Unicode characters.

Word begin and word end in JSON is only detected at whitespace and punctuation characters
from the ASCII charset, not from other Unicode charsets.

## Additional information

* Further [CQL](http://dev.folio.org/doc/glossary#cql) information.

* Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)
