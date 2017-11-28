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
