## 2.0.0 2018-01-09
 * CQLPG-32: The default relation = now uses "adj" relation for strings (was "all").
 * CQLPG-23: Support "any" relation.
 * CQLPG-33: Performance test for number match.

## 1.3.4 2018-01-05
 * CQLPG-31: CQL number match when invoked without schema
 * CQLPG-30: Trigger on postgres numbers, not only on json numbers

## 1.3.3 2017-12-19
 * CQLPG-29: "sortBy field" sorts by null
 * CQLPG-21, CQLPG-23: Documentation of new relations: all adj

## 1.3.2 2017-12-05
 * MODINVSTOR-38: Fix matching a number in a string field, performance tests
 * CQLPG-21: Implement CQL's adj keyword for phrase matching 

## 1.3.1 2017-12-01
 * UICHKOUT-39: Unit test for number in string field
 * CQLPG-4: Correctly handle numbers in number and string fields.

## 1.3.0 2017-11-28
 * CQLPG-6: Replace regexp by LIKE for <> and == for performance
 * CQLPG-9: Use trigram matching (pg_trgm)
 * CQLPG-10: Use unaccent instead of regexp for performance
 * CQLPG-11: IndexPerformanceTest unit test
 * CQLPG-12: bump external libraries to latest stable version
 * CQLPG-13: test ORDER BY performance using the output from CQL conversion
 * CQLPG-14: remove raw term in ORDER BY
 * CQLPG-18: name==123 should use lower(f_unaccent(...)) index
## 1.2.1 2017-11-14
 * Replace CASE jsonb_typeof(field) ... by an AND/OR expression (CQLPG-8)
 * For numeric values use -> not ->> to make use of the index. (CQLPG-7 partially)
## 1.2.0 2017-07-20
 * Implement multi-field queries (FOLIO-727)
## 1.1.0 2017-05-10
 * First code release  
 * Update cql-java dependency to v1.13 (FOLIO-596)
