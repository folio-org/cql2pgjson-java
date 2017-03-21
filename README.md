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
    // select * from users
    // where CAST(users.user_data->'name' AS text)
    //       ~ '(^|[[:punct:]]|[[:space:]])Miller($|[[:punct:]]|[[:space:]])'
Setting server choice indexes is possible:

    CQL2PgJSON cql2pgJson = new CQL2PgJSON("users.user_data", Arrays.asList("name", "email"));
    String cql = "Miller";
    String where = cql2pgJson.cql2pgJson(cql);
    String sql = "select * from users where " + where;

Only these relations have been implemented yet:

* `=` (substring match)
* `==` (exact match)
* `>` `>=` `<` `<=` `<>` (comparison for both strings and numbers)

Note to mask the CQL special characters by prepending a backslash: * ? ^ " \

Functional modifiers: `ignoreCase`, `respectCase` and `ignoreAccents`, `respectAccents`
are implemented for all characters (ASCII and Unicode). Default is `ignoreCase` and `ignoreAccents`.
Example for respecting case and accents:
`groupId=/respectCase/respectAccents 'd0faefc6-68c0-4612-8ee2-8aeaf058349d'`

Matching modifiers: Only `masked` is implemented, not `unmasked`, `regexp`,
`honorWhitespace`, `substring`.

Word begin and word end in JSON is only detected at whitespace and punctuation characters
from the ASCII charset, not from other Unicode charsets.

A search matching all records in the target index can be executed with a
`cql.allRecords=1` query. `cql.allRecords=1` can be used alone, or as part of
a more complex query. For example,
`cql.allRecords=1 NOT name=Smith sortBy name/sort.ascending`

All locally produced Exceptions are derived from a single parent so they can be caught collectively
or individually. Methods that load a JSON data object model pass in the identity of the model as a
resource file name, and may also throw a native `java.io.IOException`.

    CQL2PgJSONException
      ├── FieldException
      ├── SchemaException
      ├── ServerChoiceIndexesException
      ├── CQLFeatureUnsupportedException
      └── QueryValidationException
            └── QueryAmbiguousException

## Additional information

* Further [CQL](http://dev.folio.org/doc/glossary#cql) information.

* Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)

* To run the unit tests in your IDE the Unicode input files must have been produced by running maven,
  in Eclipse you may use "Run as ... Maven Build" for doing so.
