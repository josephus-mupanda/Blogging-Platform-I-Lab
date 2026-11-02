# Smart Blogging Platform

A JavaFX desktop blogging platform built on polyglot persistence: **PostgreSQL** for the
structured, relational core (users, posts, tags) and **MongoDB** for the unstructured,
variable-shape data (threaded comments and reviews). The project demonstrates database
design through to 3NF, JDBC data access with parameterized queries, and the DSA
techniques — hashing, caching, sorting, searching and indexing — that make it fast.

> **Project status:** all four phases complete. See [`phases.md`](phases.md) for each
> step and the evidence behind it.

![Post detail with a threaded MongoDB discussion and the rating distribution](docs/screenshots/05-post-detail.png)

| Document | What it holds |
|---|---|
| [`docs/01-database-design.md`](docs/01-database-design.md) | ERD, logical model, 3NF analysis, index rationale, MongoDB design |
| [`docs/02-performance-report.md`](docs/02-performance-report.md) | Before/after timings for indexing, full-text search, pagination, caching and DSA, with query plans |
| [`docs/03-sql-vs-nosql.md`](docs/03-sql-vs-nosql.md) | The same comment and review workloads on both stores, on identical data |
| [`docs/benchmarks/`](docs/benchmarks/) | The CSVs and `EXPLAIN ANALYZE` plans every number in those reports comes from |
| [`docs/screenshots/`](docs/screenshots/) | Every screen with seeded data, captured by the end-to-end UI test |

---

## Requirements

| Component | Version | Notes |
|---|---|---|
| JDK | 21 or newer | The build targets release 21. |
| Maven | 3.9+ | The bundled `mvnw` wrapper works too. |
|  PostgreSQL | 14 or newer | Needs the `pg_trgm` extension, which ships with the standard distribution. |
| MongoDB | 6.0 or newer | Plus `mongosh` for running the setup scripts. |

---

## Setup

### 1. PostgreSQL

Install the server, then create the database:

```bash
# Windows PowerShell: psql is usually at
#   C:\Program Files\PostgreSQL\16\bin\psql.exe
psql -U postgres -p 5433 -c "CREATE DATABASE blogging_platform;"
```

Run the scripts **in order** from the project root:

```bash
psql -U postgres -p 5433 -d blogging_platform -f db/postgres/V1__schema.sql
psql -U postgres -p 5433 -d blogging_platform -f db/postgres/V2__indexes.sql
psql -U postgres -p 5433 -d blogging_platform -f db/postgres/V3__seed.sql
```

`V3__seed.sql` prints a row count per table when it finishes. Expected, approximately:

| Table | Rows |
|---|---|
| users | 207 |
| posts | 508 |
| tags | 12 |
| post_tags | ~1 260 |
| comments | ~5 000 |
| reviews | ~1 980 |

> `V2__indexes.sql` is kept separate on purpose. Phase 4 benchmarks query plans with and
> without indexes, so the schema has to be usable without them. The same file carries a
> commented-out `DROP INDEX` block to return to that baseline.

### 2. MongoDB

Start `mongod`, then run the scripts in order. Run the  PostgreSQL seed **first** — the
Mongo seed references the same `postId` values.

```bash
mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/01-collections.js
mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/02-indexes.js
mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/03-seed.js
```

### 3. Configuration

Defaults live in `src/main/resources/application.properties` and assume a local server
with the `postgres`/`postgres` credentials. Override anything without editing the file:

```bash
# JVM system property
mvn javafx:run -Ddb.postgres.password=yourpassword

# or an environment variable: upper-case, dots become underscores
export DB_POSTGRES_PASSWORD=yourpassword      # PowerShell: $env:DB_POSTGRES_PASSWORD = "..."
```

Keys worth knowing:

| Key | Default | Purpose |
|---|---|---|
| `db.postgres.url` | `jdbc:postgresql://localhost:5433/blogging_platform` | JDBC connection string |
| `db.mongo.uri` | `mongodb://localhost:27017` | Mongo connection string |
| `app.comments.store` | `MONGO` | `MONGO` or `POSTGRES` — which store backs comments at runtime |
| `app.cache.enabled` | `true` | Toggles the Phase 4 in-memory cache |
| `app.pagination.pageSize` | `20` | Rows per page in post listings |

### 4. Build and run

```bash
mvn clean compile     # compile
mvn test              # run the test suite
mvn javafx:run        # launch the application
```

---

## Project layout

```
.
├── db/
│   ├── postgres/
│   │   ├── V1__schema.sql        tables, constraints, FKs, triggers, summary view
│   │   ├── V2__indexes.sql       index strategy, separable for benchmarking
│   │   └── V3__seed.sql          demo data + generated bulk data
│   ├── mongo/
│   │   ├── 01-collections.js     collections with $jsonSchema validators
│   │   ├── 02-indexes.js         index strategy
│   │   └── 03-seed.js            demo thread + bulk documents
│   └── bench/
│       └── scale.sql             100 000-post benchmark dataset (separate database)
├── docs/
│   ├── 01-database-design.md
│   ├── 02-performance-report.md
│   ├── 03-sql-vs-nosql.md
│   ├── benchmarks/               CSV results and query plans
│   └── screenshots/
├── src/main/java/org/example/bloggingplatformi/
│   ├── model/                    records: Post, Comment, Review, PostFilter, Page...
│   ├── dao/                      interfaces + JDBC and MongoDB implementations,
│   │                             CachedPostDao (LRU decorator), DaoFactory
│   ├── service/                  validation and business rules
│   ├── view/, ui/                JavaFX screens, shell, Async, design system
│   ├── cache/, algo/             LruCache, InvertedIndex, merge sort, binary search
│   ├── benchmark/                harness, PerformanceSuite, MongoBenchLoader
│   ├── db/, config/              connection pool, Mongo client, layered config
│   └── util/                     slugs, excerpts
├── src/test/java/...             unit, integration and end-to-end UI tests
├── phases.md                     the four-phase delivery plan and its status
└── README.md
```

---

## Testing

```bash
mvn test
```

The suite splits in two:

- **Unit tests** run with no database and cover the logic the service layer owns:
  slug generation and collision handling, excerpt truncation, the
  status/`published_at` pairing, tag normalization, pagination arithmetic and
  validation collection. These run against in-memory fake DAOs.
- **Integration tests** (`PostgresDaoIT`, `MongoCommentDaoIT`, `EngagementBatchIT`)
  need a running server. When one is unreachable they **skip** rather than fail, so a
  stopped service does not produce a red build that everyone learns to ignore, and a
  run without a database never reports success it did not earn. Against live servers:
  **173 tests, 0 failures, 0 skipped.**

### End-to-end UI test

```bash
mvn test -Dtest=UiCrudE2E -Dsurefire.failIfNoSpecifiedTests=false
```

`UiCrudE2E` opens the real application window and performs every CRUD operation the way
a user would: typing into fields, pressing buttons, answering confirmation dialogs. It
then checks each result in PostgreSQL and MongoDB. It covers:
- create, edit and delete a post;
- comment, reply and review;
- hide and restore a comment;
- create and delete a tag;
- deactivate and reactivate an account;
- inline validation, refusal of a duplicate review, and the cascade when a post is
  deleted.

It also regenerates `docs/screenshots/`. It needs a display, so it is not part of
`mvn test`.

### Benchmarks

The performance numbers come from a separate 100 000-post database, so the demo data is
never touched. The steps are in
[`docs/02-performance-report.md`](docs/02-performance-report.md) §9. In short:
`db/bench/scale.sql`, then `mvn javafx:run@bench -Dbench.mode=load-mongo`, then
`mvn javafx:run@bench`.

Headline results, all from [`docs/benchmarks/`](docs/benchmarks/):

| | Before | After |
|---|---:|---:|
| Default feed, 100 000 posts (composite index) | 394.0 ms | 0.30 ms |
| Post keyword search (full-text index, after fixing the query to match it) | 29.98 s | 7.86 ms |
| Page at row 50 000 (keyset instead of OFFSET) | 301.2 ms | 0.76 ms |
| 2 000 skewed post lookups (LRU cache, 79.3 % hit rate) | 388.3 ms | 74.6 ms |
| Keyword search over 100 000 titles in memory (inverted index) | 272.5 ms | 0.47 ms |

---

## Design highlights

**Comments and reviews exist in both stores.** This is deliberate. Implementing one
entity in both engines behind a single `CommentDao` interface turns the required
SQL-versus-NoSQL comparison from an essay into a measurement: Phase 4 runs identical
workloads against each and records the timings.

**Indexes are a separate script.** Performance claims need a baseline. Keeping
`V2__indexes.sql` out of the schema means the "before" half of the performance report is
a real, reproducible state rather than a recollection.

**Comment trees use a materialized path in MongoDB and an adjacency list in PostgreSQL.**
Each store gets the representation it handles well, and the two are compared directly.

**Denormalization is documented, not incidental.** `posts.view_count` and `posts.excerpt`
break 3NF on purpose; section 4 of the design document states the reason and the
mechanism that keeps each from drifting.

Full reasoning is in [`docs/01-database-design.md`](docs/01-database-design.md).

---

## Troubleshooting

**`ERROR: extension "pg_trgm" is not available`** — the contrib package is missing.
On Debian/Ubuntu install `postgresql-contrib`; on Windows re-run the EDB installer and
include the contrib modules.

**`Connection refused ... port 5432`** — PostgreSQL is not necessarily on the default
port. An installer run over an existing installation commonly lands on 5433. Check the
`port` line in `postgresql.conf`, or list what the server is listening on, and match
`db.postgres.url` to it. This project defaults to **5433**.

**`FATAL: password authentication failed for user "postgres"`** — override the default
with `-Ddb.postgres.password=...` or `DB_POSTGRES_PASSWORD`.

**`MongoServerSelectionError: connect ECONNREFUSED 127.0.0.1:27017`** — `mongod` is not
running. Start the service, or launch it with an explicit `--dbpath`.

**Mongo seed inserts fewer comments than expected** — `03-seed.js` must run after the
PostgreSQL seed, since it references `post_id` values 1 through 508.

**The benchmark suite refuses to start** — it only runs against databases whose names
contain `bench`, because it drops and recreates indexes. Build the benchmark database
first (report §9), or point `bench.postgres.url` in `pom.xml` at it.
