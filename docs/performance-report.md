# Performance report

[Back to the README](../README.md)

The [benchmark runner](../src/main/java/org/example/bloggingplatformi/benchmark/Benchmark.java) measures the optimizations used by the current implementation. This run used Java 21 and PostgreSQL 18.3 on Windows on 4 November 2026.

## Results

| Workload | Before | After | Speedup |
|---|---:|---:|---:|
| 20 `SELECT 1` queries, new connections vs. one reused connection | 9,850.39 ms | 184.73 ms | 53.3× |
| Keyword search with `ILIKE`, without vs. with trigram indexes | 1,383.36 ms | 19.79 ms | 69.9× |
| Count posts by author, without vs. with the author index | 27.79 ms | 0.48 ms | 57.9× |
| Read the newest 10 posts, without vs. with the creation-time index | 184.26 ms | 0.58 ms | 317.7× |
| Read 1,000 posts by ID, first pass vs. cached second pass | 636.76 ms | 0.60 ms | 1,061.3× |
| 1,000 lookups in 100,000 sorted integers, linear vs. binary search | 419.02 ms | 4.46 ms | 94.0× |

Speedups are calculated from the displayed timings.

## Method

- [Scale data](../db/bench/scale.sql) creates 100,000 posts, 1,000 authors, and five tags. Each post gets one tag.
- The three SQL timings are averages per query. Each query runs once to warm up and 20 more times for measurement. The runner drops the secondary indexes under test, measures the queries, applies the [index script](../db/postgres/indexes.sql), runs `ANALYZE posts`, and measures again.
- The connection, cache, and search rows are totals for the stated workload. The cache comparison repeats the same 1,000 IDs in one process. The binary-search input is already sorted.
- The benchmark uses focused queries, such as counting keyword matches and selecting the newest 10 rows. These are timings for one local run. Search selectivity, database size, connection setup, and cache hit rate affect the result.

## Reproduce

Use a fresh benchmark database. The schema script drops tables and the benchmark recreates indexes. Run these commands from the repository root.

```sh
psql -U postgres -p 5433 -c "CREATE DATABASE blogging_platform_bench;"
psql -U postgres -p 5433 -d blogging_platform_bench -v ON_ERROR_STOP=1 -f db/postgres/schema.sql
psql -U postgres -p 5433 -d blogging_platform_bench -v ON_ERROR_STOP=1 -f db/bench/scale.sql
./mvnw javafx:run@bench
```

On Windows, use `.\mvnw.cmd javafx:run@bench`. The Maven benchmark execution sets the JDBC URL to `jdbc:postgresql://localhost:5433/blogging_platform_bench`. Set `DB_POSTGRES_USER` and `DB_POSTGRES_PASSWORD` if your credentials differ from the defaults. The runner prints a Markdown table to the console.
