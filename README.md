# cql2pgjson-java

CQL (Contextual Query Language) to PostgreSQL JSON converter in Java.

## License

Copyright (C) 2016-2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Usage

Invoke like this:

    // users.user_data is a JSONB field in the users table.
    CQL2PgJSON cql2pgJson = new CQL2PgJSON("users.user_data", userSchemaJson);
    String cql = "name=Miller";
    String where = cql2pgJson.cql2pgJson(cql);
    String sql = "select * from users where " + where;
    // select * from users
    // where CAST(users.user_data->'name' AS text)
    //       ~ '(^|[[:punct:]]|[[:space:]])Miller($|[[:punct:]]|[[:space:]])'

`userSchemaJson` is the schema definition like [userdata.json](src/test/resources/userdata.json),
learn more from [raml-module-builder's documentation](https://github.com/folio-org/raml-module-builder#step-1-describe-the-apis-to-be-exposed-by-the-new-module).

Setting server choice indexes is possible, the next example searches `name=Miller or email=Miller`:

    CQL2PgJSON cql2pgJson = new CQL2PgJSON("users.user_data", userSchemaJson, Arrays.asList("name", "email"));
    String cql = "Miller";
    String where = cql2pgJson.cql2pgJson(cql);
    String sql = "select * from users where " + where;

Searching across multiple JSONB fields works like this. The _first_ json field specified
in the constructor will be applied to any query arguments that aren't prefixed with the appropriate
field name:

    // Instantiation without schemas
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(Arrays.asList("users.user_data","users.group_data"));

    // Instantiation with schemas
    LinkedHashMap<String,String> fieldsAndSchemas = new LinkedHashMap<>();
    fieldsAndSchemas.put("users.user_data",         userSchemaJson);
    fieldsAndSchemas.put("users.group_data",        groupSchemaJson);
    fieldsAndSchemas.put("users.uncontrolled_data", null);
    cql2pgJson = new CQL2PgJSON( fieldsAndSchemas );

    // Query processing
    where = cql2pgJson.cql2pgJson( "users.user_data.name=Miller" );
    where = cql2pgJson.cql2pgJson( "users.group_data.name==Students" );
    where = cql2pgJson.cql2pgJson( "users.uncontrolled_data.name==*Zanzibar*" );
    where = cql2pgJson.cql2pgJson( "name=Miller" ); // implies users.user_data

## Schema

If invoked without schema specification like `userSchemaJson` then the produced SQL code
does not contain optimizations to use database indexes and may run with bad performance on
large datasets.

## Relations

Only these relations have been implemented yet:

* `=` (this is `==` for a number and `adj` for a string.
       Examples 1: `height = 3.4` Example 2: `title = Potter`)
* `==` (exact match, for example `barcode == 883746123` or exact substring match `title == "*Harry Potter*"`;
        numeric fields match any form: 3.4 = 3.400 = 0.34e1)
* `all` (each word of the query string exists somewhere, `title all "Potter Harry"` matches "Harry X. Potter")
* `any` (any word of the query string exists somewhere, `title any "Potter Foo"` matches "Harry Potter")
* `adj` (substring phrase match: all words of the query string exist consecutively in that order, there may be any
          whitespace and punctuation in between, `title adj "Harry Potter"` matches "Harry - . - Potter")
* `>` `>=` `<` `<=` `<>` (comparison for both strings and numbers)

Note to mask the CQL special characters by prepending a backslash: * ? ^ " \

Use quotes if the search string contains a space, for example `title = "Harry Potter"`.

## Modifiers

Functional modifiers: `ignoreCase`, `respectCase` and `ignoreAccents`, `respectAccents`
are implemented for all characters (ASCII and Unicode). Default is `ignoreCase` and `ignoreAccents`.
Example for respecting case and accents:
`groupId==/respectCase/respectAccents 'd0faefc6-68c0-4612-8ee2-8aeaf058349d'`

Matching modifiers: Only `masked` is implemented, not `unmasked`, `regexp`,
`honorWhitespace`, `substring`.

Word begin and word end in JSON is only detected at whitespace and punctuation characters
from the ASCII charset, not from other Unicode charsets.

## Matching all records

A search matching all records in the target index can be executed with a
`cql.allRecords=1` query. `cql.allRecords=1` can be used alone or as part of
a more complex query, for example
`cql.allRecords=1 NOT name=Smith sortBy name/sort.ascending`

* `cql.allRecords=1 NOT name=Smith` matches all records where name does not contain Smith
   as a word or where name is not defined.
* `name="" NOT name=Smith` matches all records where name is defined but does not contain
   Smith as a word.

## Matching array elements

For matching the elements of an array use these queries (assuming that lang is either an array or not defined, and assuming
an array element value does not contain double quotes):
* `lang ==/respectAccents []` for matching records where lang is defined and an empty array
* `cql.allRecords=1 NOT lang <>/respectAccents []` for matching records where lang is not defined or an empty array
* `lang =/respectCase/respectAccents \"en\"` for matching records where lang is defined and contains the value en
* `cql.allRecords=1 NOT lang =/respectCase/respectAccents \"en\"` for matching records where lang does not
  contain the value en (including records where lang is not defined)
* `lang = "" NOT lang =/respectCase/respectAccents \"en\"` for matching records where lang is defined and
  and does not contain the value en
* `lang = ""` for matching records where lang is defined
* `cql.allRecords=1 NOT lang = ""` for matching records where lang is not defined
* `identifiers == "*\"value\": \"6316800312\", \"identifierTypeId\": \"8261054f-be78-422d-bd51-4ed9f33c3422\"*"`
  (note to use `==` and not `=`) for matching the ISBN 6316800312 using ISBN's identifierTypeId where each element of
  the identifiers array is a JSON object with the two keys value and identifierTypeId, for example

      "identifiers": [ {
        "value": "(OCoLC)968777846", "identifierTypeId": "7e591197-f335-4afb-bc6d-a6d76ca3bace"
      }, {
        "value": "6316800312", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"
      } ]

## Matching and comparing numbers

Correct number matching must result in 3.4 = 3.400 = 0.34e1 and correct number comparison must result in 10 > 2
(in contrast to string comparison where "10" > "2").

If the search term is a number then a numeric mode is used for "==", "<>", "<", "<=", ">", ">=", and "=".

If the schema (see section [Schema](#schema) above) specifies a JSON field type
then numeric mode is only used when that type is either `integer` or `number`.

If the comparator is "=" and the JSON field type is not specified in the schema
then numeric mode is only used if the actual JSONB type of the stored value is `number`
(JSONB has no `integer` type).

## Exceptions

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

* See project [CQLPG](https://issues.folio.org/browse/CQLPG)
at the [FOLIO issue tracker](http://dev.folio.org/community/guide-issues).

* Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)

* To run the unit tests in your IDE the Unicode input files must have been produced by running maven,
  in Eclipse you may use "Run as ... Maven Build" for doing so.
