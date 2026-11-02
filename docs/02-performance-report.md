# Performance Report — Smart Blogging Platform

**Version:** 1.0 · **Date:** 2026-11-02 · **Phase:** 4 · **Covers:** Epic 3 (US 3.1, 3.2), Epic 4 (US 4.1)

Every number below comes from a CSV written by `benchmark/PerformanceSuite.java` into
[`docs/benchmarks/`](benchmarks/). None was typed in by hand. The query plans behind the
index results are in [`docs/benchmarks/plans/`](benchmarks/plans/), one file per query
per phase.

---

## 1. Summary

| Technique | Workload | Before | After | Gain |
|---|---|---:|---:|---:|
| B-tree index | default feed, 100 000 posts | 394.0 ms | 0.30 ms | **1,323×** |
| Partial index | moderation queue, 630 000 comments | 253.3 ms | 0.32 ms | **784×** |
| Expression index | case-insensitive title lookup | 305.3 ms | 1.26 ms | **241×** |
| Trigram GIN index | title substring count (`ILIKE '%…%'`) | 316.9 ms | 11.6 ms | **27×** |
| Full-text GIN index, **after fixing the query** | post keyword search, as the app runs it | 29.98 s | 7.86 ms | **3,816×** |
| Keyset pagination | page at row 50 000 | 301.2 ms | 0.76 ms | **398×** |
| LRU cache (200 entries) | 2 000 lookups, 80 % to a hot set | 388.3 ms | 74.6 ms | **5.2×** at a 79.3 % hit rate |
| Inverted index (hashing) | keyword search over 100 000 titles, in memory | 272.5 ms | 0.47 ms | **580×** |
| Binary search | 1 000 lookups in 100 000 sorted ids | 154.9 ms | 0.39 ms | **397×** |

Measuring also found **five defects** that no functional test could have caught, because
each one returned correct results, just slowly. Section 3 covers them. The worst was
the post search: its query never used the full-text index built for it. Each search
computed a text vector for all 100 000 posts and took **15 seconds** even with the
index in place.

---

## 2. Method

### 2.1 Dataset

The demo seed (508 posts) is useless for this purpose: every table fits in a few pages,
and PostgreSQL rightly seq-scans it whether indexes exist or not. The benchmark runs
against a separate database, built by [`db/bench/scale.sql`](../db/bench/scale.sql):

| Table | Rows | Notes |
|---|---:|---|
| users | 5 000 | |
| posts | 100 000 | 4 years of dates, 80 % published, ~730-character bodies from a 150-word vocabulary |
| post_tags | 248 728 | 200 tags, 1–4 per post |
| comments | 630 000 | 500 000 top level, 100 000 replies, 30 000 replies to replies |
| reviews | 259 930 | one per (post, reviewer) |

The text is drawn from vocabularies rather than repeated, plus synthetic rare terms
(`term0000`–`term1999` in posts, `kw0000`–`kw4999` in comments). Each rare term matches
about a hundred rows, so a search has a realistic selectivity. A benchmark that only
searches for a word present in every row says nothing useful about an index.

MongoDB holds **the same** comments and reviews, copied row for row from PostgreSQL by
`MongoBenchLoader`. The materialized paths are derived from the relational parent ids.
All 890 000 documents passed the collections' strict `$jsonSchema` validators.

### 2.2 Measurement

- **Harness:** `benchmark/Benchmark.java`. Each operation gets warm-up iterations, then
  N timed iterations with `System.nanoTime()`. The headline is the **median**; mean,
  p95 and max are in the CSVs. Every result feeds a checksum, so the JIT cannot eliminate
  the work.
- **Iterations:** 21 for most queries, 5 for the multi-second unindexed full-text
  scans, and 51 for the store comparison. Each CSV records its own count.
- **Fetching is included.** Every row is read from the `ResultSet`, so the time covers
  the whole query, not just the moment the first row arrives.
- **Inputs rotate.** Each iteration uses a different parameter from a fixed, seeded
  sample (50 authors, 50 titles, 20 rare terms…), so one lucky value cannot dominate.
- **Indexes are toggled from the real script.** The suite reads every
  `CREATE INDEX` from `V2__indexes.sql`, drops them all and runs `ANALYZE`, measures, then
  creates them and runs `ANALYZE` again. The benchmark therefore cannot drift from the
  shipped index set.
- **Correctness before speed.** Before any timing, the suite checks that:
  - keyset and OFFSET return the same page;
  - the cache returns the same post as the database;
  - the inverted index agrees with a scan;
  - merge sort agrees with `List.sort`;
  - both stores return the same comment thread in the same order.

  A fast wrong answer is not a result.

### 2.3 Environment and limits

Recorded in [`benchmarks/environment.txt`](benchmarks/environment.txt): Java 21.0.2 on
Windows 11, 4 cores, PostgreSQL 18.3 (`shared_buffers` 128 MB, `work_mem` 4 MB,
defaults) and MongoDB 9.0.4, all on localhost, one client.

Read the numbers with three limits in mind:

1. **Unindexed timings vary between runs.** The suite was run twice; the first run is
   kept in [`benchmarks/run-1-before-fixes/`](benchmarks/run-1-before-fixes/). Indexed
   timings agreed closely between the runs (e.g. `published_feed` 0.27 ms vs 0.30 ms).
   Unindexed ones did not: `posts_by_author` without an index took 63.2 ms in run 1
   and 257.4 ms in run 2. A sequential scan over a 113 MB table with a 128 MB buffer
   pool depends on what the OS cache and the other processes on a 4-core laptop are
   doing at that moment. **The speed-ups are reliable to an order of magnitude, not
   to the digit.**
2. **Warm cache, localhost.** These are server execution plus one local round trip. A
   networked deployment adds latency to every query, which narrows the ratios for fast
   queries without changing which plan wins.
3. **This is not JMH.** It is adequate where the difference is a factor. Section 8
   shows where it is not.

---

## 3. Defects found by measuring

Each of these returned correct results. Only the timings showed that something was
wrong, and each was then confirmed with `EXPLAIN` before being changed.

| # | Defect | Measured before | After fix | Fix |
|---|---|---:|---:|---|
| 1 | Post search never used the full-text index | 15.35 s *with* the index | 1.14 ms | Query repeats the indexed expression exactly |
| 2 | MongoDB subtree read scanned the whole collection | 2.12 s | 6.27 ms | Filter includes `postId`; plain anchored regex |
| 3 | MongoDB delete-with-replies scanned the whole collection | 858.4 ms | 6.02 ms | Same filter as #2 |
| 4 | MongoDB moderation queue could not use its partial index | 438.4 ms | 3.19 ms | `$in` over non-visible statuses instead of `$ne` |
| 5 | PostgreSQL moderation queue sorted the whole queue | 25.4 ms (raw), 69.6 ms (DAO) | 0.32 ms, 1.66 ms | Index keyed on `created_at` instead of `(status, created_at)` |

Related, but not a defect: comment keyword search in PostgreSQL had no index at all
(1.63 s). It now has one; see 4.2.

**1 — Expression index mismatch.** `idx_posts_search_tsv` is built on
`setweight(to_tsvector('english', coalesce(title,'')),'A') || setweight(…body…,'B')`,
but `JdbcPostDao` searched with `to_tsvector('english', title || ' ' || body)`. Both
find the same posts, but PostgreSQL matches expression indexes structurally, not by
meaning. With the index present, the old query's plan was still a parallel sequential
scan: [`fts_old_dao_expression--indexed.txt`](benchmarks/plans/fts_old_dao_expression--indexed.txt).
The query now repeats the indexed expression character for character.

**2, 3 — Index prefix not supplied.** The index is `{postId: 1, path: 1}`, and the
subtree filter gave only `path`, so MongoDB could not use the index:
`explain` showed 630 000 documents examined. With `postId` added it examined **2**.
The regex was also built with `Pattern.quote`, which produces `^\Q…\E`; MongoDB only
turns a plain `^prefix` into index bounds. The prefix is still escaped, character by
character (`MongoRegexEscapeTest`).

**4 — `$ne` versus a partial index.** The index only covers
`status ∈ {HIDDEN, FLAGGED}`. A query using `status: {$ne: 'VISIBLE'}` means the same
thing, but MongoDB cannot prove that it implies the index's filter, so it scanned the
collection. `$in` with the same statuses reads 50 index entries.

**5 — Wrong leading column.** With `(status, created_at DESC)`, a queue spanning two
statuses is two separate index ranges. PostgreSQL read all 15 051 queued rows through
a bitmap scan and then sorted them to keep 50. Keyed on `created_at` alone (still
partial on `status <> 'VISIBLE'`), it reads the newest 50 entries and stops.

---

## 4. Indexing (US 3.1, US 4.1)

### 4.1 Before and after `V2__indexes.sql`

Source: [`benchmarks/indexes.csv`](benchmarks/indexes.csv), plans in
[`benchmarks/plans.csv`](benchmarks/plans.csv).

| Query | No index | Indexed | Speed-up | Plan: without ⟶ with index |
|---|---:|---:|---:|---|
| `posts_by_author` | 257.4 ms | 1.54 ms | **168×** | Seq Scan + Sort ⟶ Bitmap Index Scan |
| `published_feed` | 394.0 ms | 0.30 ms | **1,323×** | Parallel Seq Scan + Sort ⟶ Index Scan `idx_posts_status_published` |
| `most_viewed_published` | 250.5 ms | 0.32 ms | **781×** | Parallel Seq Scan + Sort ⟶ Index Scan `idx_posts_views_published` (partial) |
| `title_exact_case_insensitive` | 305.3 ms | 1.26 ms | **241×** | Seq Scan ⟶ Index Scan `idx_posts_title_lower` |
| `title_substring_count` | 316.9 ms | 11.6 ms | **27×** | Seq Scan ⟶ Bitmap Index Scan `idx_posts_title_trgm` |
| `posts_by_tag` | 128.5 ms | 7.71 ms | **17×** | Parallel Seq Scan ⟶ Index Scan `idx_posts_created_at` + Index Only Scan `idx_post_tags_tag_id` |
| `comments_for_post` | 221.0 ms | 1.01 ms | **220×** | Parallel Seq Scan ⟶ Bitmap Index Scan `idx_comments_post_created` |
| `replies_to_comment` | 202.7 ms | 0.41 ms | **498×** | Parallel Seq Scan ⟶ Index Scan `idx_comments_parent` (partial) |
| `comments_by_author` | 181.2 ms | 3.43 ms | **53×** | Parallel Seq Scan ⟶ Bitmap Index Scan `idx_comments_author` |
| `moderation_queue` | 253.3 ms | 0.32 ms | **784×** | Parallel Seq Scan + Sort ⟶ Index Scan `idx_comments_moderation` (partial) |
| `review_stats_for_page` | 8.07 ms | 0.70 ms | **11.5×** | Bitmap scan on the unique constraint ⟶ **Index Only Scan** `idx_reviews_post_rating` |
| `reviews_by_reviewer` | 201.0 ms | 1.35 ms | **149×** | Parallel Seq Scan ⟶ Index Scan `idx_reviews_reviewer` |

What the plans show:

- **The biggest gains come from removing a sort, not a scan.** In `published_feed`,
  `most_viewed_published` and `moderation_queue`, the index is already in the order the
  query asks for. PostgreSQL reads 20 or 50 entries and stops, so there is no Sort
  node at all. This is why the composite and partial indexes put the sort column where
  they do.
- **`review_stats_for_page` was already fast without V2.** The
  `UNIQUE (post_id, reviewer_id)` constraint creates an index whose leading column is
  `post_id`. V2's covering `(post_id, rating)` index turns that into an
  *index-only* scan, which never touches the table: 11.5×.
- **Substring search gains the least (27×).** A trigram index narrows the candidates,
  but every candidate row still has to be rechecked against the pattern, and a common
  fragment like `keyset` matches thousands of titles.

### 4.2 Comment keyword search

`JdbcCommentDao.search` uses `body ILIKE '%term%'`. Phase 4 added
`idx_comments_body_trgm` (GIN, trigram):

| | Before (no index) | After |
|---|---:|---:|
| `keyword_search_comments`, PostgreSQL DAO | 1.63 s | 25.7 ms (**64×**) |

Source: `sql-vs-nosql.csv` in run 1 and run 2. The cost is index size: the comments
table's indexes grew from 41.9 MB to 94.2 MB ([`storage.csv`](benchmarks/storage.csv)).

### 4.3 What indexes cost

- **Build time:** creating all 17 indexes on this dataset took 97.7 s, against 37.4 s
  for the 16 in run 1. Almost all of the difference is the new trigram index on 630 000
  comment bodies.
- **Writes:** inserting a comment took a median of 1.55 ms in run 1 and 1.71 ms in
  run 2, after the trigram index was added. The difference is within the noise this
  harness can resolve, so the write cost is real but small.
- **Space:** see 4.2.

Indexes that were **not** created, on purpose, are listed in the design document §5:
low-cardinality columns such as `status` or `role` alone, where a sequential scan beats
an index.

---

## 5. Full-text search (US 3.1)

Source: [`benchmarks/fulltext.csv`](benchmarks/fulltext.csv). Query: posts matching a
rare term (≈100 of 100 000).

| Query | No index | Index present | Speed-up |
|---|---:|---:|---:|
| Old DAO expression, `to_tsvector(title \|\| ' ' \|\| body)` | 16.89 s | **15.35 s** | 1.1× (no index use) |
| Indexed expression, `setweight(…) \|\| setweight(…)` | 12.78 s | **1.14 ms** | 11,236× |
| `JdbcPostDao.search` end to end (COUNT + page, title `ILIKE` **or** full-text) | 29.98 s | **7.86 ms** | 3,816× |

With the index present in both cases, the old expression and the indexed one differ by
**13,498×**. The application's search is a `COUNT` plus a page query, and each OR's a
trigram title match with the full-text match. It runs in 7.86 ms because both arms
are now indexable, so PostgreSQL combines two bitmap index scans with a `BitmapOr`
([`dao_search_keyword--indexed.txt`](benchmarks/plans/dao_search_keyword--indexed.txt).
Before the fix, one arm used its index and the other read every post.

The search is case-insensitive in both arms: `ILIKE`, and the English text-search
configuration lower-cases tokens. This satisfies US 3.1's first acceptance criterion.

---

## 6. Pagination: OFFSET versus keyset

Source: [`benchmarks/pagination.csv`](benchmarks/pagination.csv). Both queries return
20 posts in `created_at DESC, post_id DESC` order; the suite checks that both return
the same 20 ids at every depth.

| Rows skipped | `LIMIT 20 OFFSET n` | Keyset `WHERE (created_at, post_id) < (?, ?)` | Keyset advantage |
|---:|---:|---:|---:|
| 0 | 0.63 ms | 0.70 ms | same query |
| 1 000 | 2.62 ms | 0.97 ms | 2.7× |
| 10 000 | 18.8 ms | 0.83 ms | 23× |
| 50 000 | 301.2 ms | 0.76 ms | **398×** |
| 99 000 | 271.1 ms | 0.85 ms | 318× |

OFFSET has to produce and discard every skipped row, so its cost grows with depth.
Past a point the planner stops using the index and sorts the table: at row 50 000 the
plan is `Seq Scan → Sort`. Keyset seeks straight to the cursor in `idx_posts_created_at`
and reads 21 entries (`pagination_keyset_50000--indexed.txt`). Its cost is flat.

**Decision for the application:** the post list keeps page-number (OFFSET) pagination.
It lets a user jump to "page 7 of 26" and shows a total, which keyset cannot do without
a separate count. At the demo's 26 pages, the deepest page costs about 1 ms. Keyset is
the recommendation for any feed that only needs "next": infinite scroll, API cursors,
exports. The measurement above is what justifies the switch if the post count grows
toward the benchmark's scale.

---

## 7. Caching (US 3.2)

Source: [`benchmarks/cache.csv`](benchmarks/cache.csv),
[`benchmarks/cache-stats.csv`](benchmarks/cache-stats.csv).

`CachedPostDao` decorates the JDBC post DAO with an `LruCache` (a `LinkedHashMap` in
access order, so O(1) get and put) plus a hash index from slug to id.

| Workload | Uncached | LRU cache, 200 entries | Gain |
|---|---:|---:|---:|
| 2 000 `findById`, 80 % to 100 hot posts, 20 % spread over 100 000 | 388.3 ms | 74.6 ms | **5.2×** |
| One hot `findById` | 0.17 ms | 0.4 µs | 430× |

Hit rate over the measured runs: **79.3 %** (15 853 hits, 4 147 misses,
3 947 evictions). That matches the workload's shape: almost every hot-set read hits, and
almost every cold read misses and evicts. The remaining 74.6 ms is those misses. The
gain is bounded by the miss rate, not by the cache: if 20 % of reads miss, no cache can
remove more than about 80 % of the time.

**Consistency.** A cache that serves a stale post is worse than no cache.
`CachedPostDaoTest` (15 tests) checks every write path:
- an update is visible immediately, even when no row changed;
- a deleted post leaves the cache, and its slug stops resolving;
- an insert does not cache the unsaved object;
- a view increments the cached copy while the database stays authoritative;
- searches and `existsBySlug` are never answered from the cache.

It uses a recording DAO, so a stale read shows up directly as a database call that
should have happened and did not. The
cache wraps the DAO rather than living in the service, so no caller can forget to
invalidate.

---

## 8. In-memory data structures and algorithms (Epic 3)

Source: [`benchmarks/dsa.csv`](benchmarks/dsa.csv). All over the 100 000 benchmark posts,
loaded into memory.

| Operation | Naive | Structured | Gain |
|---|---:|---:|---:|
| Keyword search in titles (`InvertedIndex`, `Map<String, Set<Long>>`) | linear scan 272.5 ms | 0.47 ms | **580×** |
| 100 lookups by slug | linear scan 233.2 ms | `HashMap` 7.6 µs | ~30 000× |
| 1 000 lookups in sorted ids | `linearSearch` 154.9 ms | `binarySearch` 0.39 ms | **397×** |
| Sort 100 000 posts by views | `List.sort` (TimSort) 110.5 ms | own `mergeSort` 97.8 ms | ~1.1× (parity) |

- **Inverted index.** Building it takes 1.35 s for 100 000 titles. It pays for itself
  after about five searches. Lookups are hash probes plus an intersection that starts
  from the smallest posting list.
- **Sorting: no winner claimed.** Run 1 measured 123.7 ms vs 295.1 ms in merge sort's
  favour; run 2 measured 97.8 ms vs 110.5 ms. Two runs disagreeing by 2.4× is noise,
  not a result, and exactly the kind of difference this harness cannot resolve. Both
  are O(n log n) stable sorts, and the honest conclusion is that they are at parity on
  this input. `Sorting.mergeSort` exists to show the algorithm, verified against
  `List.sort` on every run, not to replace the library.

---

## 9. Reproducing

```bash
# 1. PostgreSQL benchmark database (about a minute)
psql -U postgres -p 5433 -c "CREATE DATABASE blogging_platform_bench"
psql -U postgres -p 5433 -d blogging_platform_bench -f db/postgres/V1__schema.sql
psql -U postgres -p 5433 -d blogging_platform_bench -f db/bench/scale.sql

# 2. MongoDB copy of the same comments and reviews
mongosh "mongodb://localhost:27017/blogging_platform_bench" --file db/mongo/01-collections.js
mvn javafx:run@bench -Dbench.mode=load-mongo
mongosh "mongodb://localhost:27017/blogging_platform_bench" --file db/mongo/02-indexes.js

# 3. Run everything (about 15 minutes); writes docs/benchmarks/
mvn javafx:run@bench
```

The suite refuses to run unless both database names contain `bench`, because it drops
and recreates indexes. Connection settings are the `bench.*` properties in `pom.xml`.

---

## 10. Changes made because of this report

| Change | File |
|---|---|
| Full-text query repeats the indexed expression | `dao/JdbcPostDao.java` |
| Subtree and delete filters include `postId`, plain anchored prefix | `dao/MongoCommentDao.java` |
| Moderation queue uses `$in` | `dao/MongoCommentDao.java` |
| `idx_comments_moderation` re-keyed on `created_at` | `db/postgres/V2__indexes.sql` |
| `idx_comments_body_trgm` added | `db/postgres/V2__indexes.sql` |
