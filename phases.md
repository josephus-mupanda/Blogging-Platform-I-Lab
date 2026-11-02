# Blogging Platform I — Phase Plan

**Stack (locked 2026-11-01):** PostgreSQL 18 on **port 5433** (primary relational store)
+ MongoDB (unstructured comments & reviews), JavaFX 21 UI, JDBC + MongoDB sync driver,
Java 21, Maven. Both database servers are installed natively (no Docker).

---

## How this file works

This is the living record of the project. **Every step below carries a status, and that
status is updated the moment the step changes state** — not at the end of a phase.

| Status | Meaning |
|:---:|---|
| `DONE` | Finished **and** verified. The "Evidence" column says how it was verified. |
| `WIP` | Currently being worked on. At most one step per phase should be `WIP`. |
| `PENDING` | Planned, not started, nothing blocking it. |
| `BLOCKED` | Cannot start or finish until something external is resolved. The blocker is named. |

**Rules for keeping it honest:**

1. A step becomes `DONE` only when there is evidence — a passing test, a successful
   script run, a screenshot. "The code is written" is `WIP`, not `DONE`.
2. If a step is `DONE` but was verified at a weaker level than intended (unit tests
   rather than against a live database, say), the Evidence column states that plainly.
3. A `BLOCKED` step names its blocker and the step that will unblock it.
4. The change log at the bottom gets one line per working session.

---

## Phase overview

| Phase | Title | Epics covered | Points at stake | Status |
|:---:|---|---|:---:|:---:|
| 1 | Foundation & Database Design | Epic 1, Epic 4.2 (schema), Epic 5 (setup docs) | 25 + part of 10 | `DONE` |
| 2 | Persistence Layer (JDBC + Mongo) | Epic 2.1, 2.2 (data), Epic 4.2 (impl) | 20 + half of 20 | `DONE` |
| 3 | JavaFX Application | Epic 2.1, 2.2 (UI) | half of 20 | `DONE` |
| 4 | DSA, Optimization, Performance & Docs | Epic 3, Epic 4.1, Epic 5 | 15 + 10 + part of 10 | `WIP` |

---

## Phase 1 — Foundation & Database Design `DONE`

**Goal:** A defensible data model and a runnable skeleton, before a line of query code.

| # | Step | Status | Evidence |
|:---:|---|:---:|---|
| 1.1 | Conceptual model: entities, relationships, business rules | `DONE` | `docs/01-database-design.md` §2, Mermaid ERD |
| 1.2 | Logical model: attributes, keys, constraints per table | `DONE` | `docs/01-database-design.md` §3 |
| 1.3 | Physical schema: 6 tables, 8 FKs, 14 CHECKs, triggers, summary view | `DONE` | `db/postgres/V1__schema.sql` |
| 1.4 | Normalization to 3NF + documented exceptions | `DONE` | `docs/01-database-design.md` §4 |
| 1.5 | Index strategy, kept in a separate script for benchmarking | `DONE` | `db/postgres/V2__indexes.sql`, 16 indexes |
| 1.6 | Referential integrity rules (CASCADE vs SET NULL, with reasons) | `DONE` | `docs/01-database-design.md` §6 |
| 1.7 | MongoDB collection design with `$jsonSchema` validators | `DONE` | `db/mongo/01-collections.js` |
| 1.8 | MongoDB index strategy | `DONE` | `db/mongo/02-indexes.js` |
| 1.9 | Seed data: demo rows + bulk volume for benchmarking | `DONE` | `V3__seed.sql`, `03-seed.js`; simulated 5 005 comments / 1 500 reviews |
| 1.10 | Maven dependencies, `module-info`, externalised config | `DONE` | `mvn clean compile` passes |
| 1.11 | README with native setup instructions | `DONE` | `README.md` |

---

## Phase 2 — Persistence Layer `DONE`

**Goal:** Every database operation the UI will need, fully testable without JavaFX.

| # | Step | Status | Evidence |
|:---:|---|:---:|---|
| 2.1 | Domain models + `Page`, `PostFilter`, `PostSort`, `PostSummary` | `DONE` | `ModelInvariantsTest`, `PaginationTest` — 25 tests |
| 2.2 | `Database`: HikariCP pool, `withConnection`, `inTransaction` | `DONE` | Compiles; exercised by the IT suite |
| 2.3 | `MongoProvider`: one shared client, bounded timeouts | `DONE` | Compiles; probe used by `Databases` test helper |
| 2.4 | `UserDao` + `JdbcUserDao` | `DONE` | `PostgresDaoIT.Users` (written, skips without a DB) |
| 2.5 | `PostDao` + `JdbcPostDao` with filter/sort/pagination | `DONE` | `PostgresDaoIT.Posts` (written, skips without a DB) |
| 2.6 | `TagDao` + `JdbcTagDao` with atomic `findOrCreate` | `DONE` | Covered via `PostServiceTest` fakes + IT |
| 2.7 | `CommentDao` — **two** implementations (JDBC + Mongo) | `DONE` | `MongoCommentDaoIT` (written, skips without a DB) |
| 2.8 | `ReviewDao` — **two** implementations (JDBC + Mongo) | `DONE` | `MongoCommentDaoIT.reviewUniqueness` etc. |
| 2.9 | `DaoFactory` selecting the store from `app.comments.store` | `DONE` | Compiles; used by services |
| 2.10 | Service layer: User, Post, Tag, Comment, Review | `DONE` | `PostServiceTest` — 16 tests |
| 2.11 | Exception hierarchy + field-level validation collector | `DONE` | `ValidationExceptionTest` — 8 tests |
| 2.12 | Utilities: `Slugs`, `Excerpts` | `DONE` | `SlugsTest` (15), `ExcerptsTest` (7) |
| 2.13 | Test suite with graceful skip when a database is absent | `DONE` | `mvn test` — **166 run, 0 failures, 0 skipped** against live servers |
| 2.14 | **Execute** the SQL/Mongo scripts against live servers | `DONE` | All six scripts run clean on PostgreSQL 18.3 / MongoDB 9.0.4; `PostgresDaoIT` (14) and `MongoCommentDaoIT` (15) pass live |

> **Gap closed 2026-11-02.** Running against live servers found three real defects that
> unit tests could not: the SQL seed generated skewed data, Mongo threads were ordered
> wrongly, and one IT mutated seeded data. See the change log for each.

---

## Phase 3 — JavaFX Application `DONE`

**Goal:** A professional, interactive desktop client that exercises the whole service
layer — not a default-skinned JavaFX form, but something that reads as a real content
management console.

| # | Step | Status | Evidence |
|:---:|---|:---:|---|
| 3.0 | Bring both databases up and run the scripts (unblocks 2.14) | `DONE` | `blogging_platform` created on both servers; row counts in the change log |
| 3.1 | Design system: colour tokens, typography scale, spacing, elevation | `DONE` | `css/app.css`, ~660 lines; app launches with zero CSS parse warnings |
| 3.2 | Application shell: sidebar nav, top bar, content host, routing | `DONE` | `ui/AppShell.java`; verified in `docs/screenshots/01-dashboard-no-database.png` |
| 3.3 | UI infrastructure: `Async`, `Dialogs`, `Session`, `Icons`, `Styles`, `View` | `DONE` | `ui/` package; every service call goes through `Async` |
| 3.4 | Dashboard: KPI tiles, tag usage, recent activity | `DONE` | `view/DashboardView.java`; tiles render, screenshot attached |
| 3.5 | Post list: table, search, author/tag/status filters, sort, pagination | `DONE` | `view/PostListView.java`; renders with filter bar + empty state |
| 3.6 | Post editor: create/update/delete, tag picker, inline validation | `DONE` | `view/PostEditorView.java`; field-level error mapping from `ValidationException` |
| 3.7 | Post detail: body, threaded comments, reviews, rating distribution | `DONE` | `view/PostDetailView.java`; indents on `Comment.depth()` |
| 3.8 | Moderation queue: flagged/hidden comments, restore and delete | `DONE` | `view/ModerationView.java`; admin-gated |
| 3.9 | Graceful degradation when a database is unreachable | `DONE` | Screenshot shows the banner + em-dash tiles, no crash, no stack trace |
| 3.10 | Remove the `HelloApplication` scaffold; wire the new entry point | `DONE` | Scaffold deleted; `pom.xml` mainClass + `module-info` updated; `mvn compile` passes |
| 3.11 | Verify the app launches without an unhandled exception | `DONE` | Ran under `mvn javafx:run`; log clean, window captured |
| 3.14 | Responsive layout: no overflow on a 1280x720 logical screen | `DONE` | Window 1882x1004 physical inside a 1920x1008 work area; 4 screens captured DPI-aware, no horizontal scrollbar |
| 3.12 | Verify every CRUD path end to end against a live database | `DONE` | `ui/UiCrudE2E` — 13 steps drive the real window (typing, clicking, confirming dialogs) and check each result in PostgreSQL and MongoDB: create/edit/delete post, comment, reply, review, tag, hide/restore, deactivate/reactivate, inline validation, duplicate-review refusal, cascade on delete |
| 3.13 | Screenshots of each screen with real data, for Testing Evidence | `DONE` | 8 captures in `docs/screenshots/`, written by `UiCrudE2E` at the display's real pixel density |
| 3.15 | Fix defects visible only with real data | `DONE` | Five found by reading the captures; see the 2026-11-02 change log entry |

> **How CRUD is verified.** `UiCrudE2E` opens a real window, so it is excluded from
> `mvn test` and run on demand:
> `mvn test -Dtest=UiCrudE2E -Dsurefire.failIfNoSpecifiedTests=false`.
> It never calls a service to *do* anything: services are used only afterwards, to read
> the databases and confirm what the screen claimed. What the screenshot proves is that
> the application starts, renders and degrades gracefully — not that saving a post works.

**Exit criteria**

- `mvn clean javafx:run` launches without an unhandled exception. **Met.**
- Every CRUD path works from the UI, against a live database. **Met** — step 3.12.
- No database call runs on the JavaFX application thread. **Met** — every call goes
  through `Async`, which runs on a daemon pool and marshals results back.
- The UI is visually coherent: one type scale, one colour palette, consistent spacing.
  **Met** — all tokens live in `app.css`; no view hard-codes a colour except the six
  metric-tile accents.

**Maps to:** JavaFX + JDBC Integration (20 pts).

---

## Phase 4 — DSA, Optimization, Performance & Docs `PENDING`

**Goal:** Measured improvement, not claimed improvement.

| # | Step | Status | Evidence |
|:---:|---|:---:|---|
| 4.1 | `LruCache` (LinkedHashMap, access-order) + hash index by slug/id | `DONE` | `cache/LruCache.java`, `dao/CachedPostDao.java`; `LruCacheTest` — 15 tests incl. eviction order, LRU promotion, concurrency |
| 4.2 | Cache invalidation on every write, proven by test (no stale read) | `DONE` | `CachedPostDaoTest` — 15 tests; a recording DAO counts calls, so a hit is a call that did not happen and a stale read is caught directly |
| 4.3 | Inverted index `Map<String, Set<Long>>` for O(1) keyword candidates | `DONE` | `algo/InvertedIndex.java`; 11 tests, incl. agreement with a linear scan over 400 documents |
| 4.4 | Own merge sort + binary search | `DONE` | `algo/Sorting.java`; 15 tests, incl. stability and agreement with `Collections.sort`/`binarySearch` on random data |
| 4.5 | `EXPLAIN ANALYZE` captured before/after `V2__indexes.sql` | `PENDING` | Unblocked by 3.0 |
| 4.6 | B-tree vs GIN/trigram comparison for text search | `PENDING` | Unblocked by 3.0 |
| 4.7 | Keyset vs OFFSET pagination at depth | `PENDING` | Unblocked by 3.0 |
| 4.8 | Benchmark harness: warm-up, N iterations, median, CSV output | `DONE` | `benchmark/Benchmark.java`, `BenchmarkResult.java`; 10 tests, incl. proof the median ignores an injected outlier |
| 4.9 | SQL vs NoSQL: same workloads through both `CommentDao` impls | `PENDING` | Harness ready, data loaded |
| 4.10 | `docs/02-performance-report.md` with real numbers | `BLOCKED` | Waits on measurements from 4.5–4.9; writing it first would mean inventing the numbers |
| 4.11 | `docs/03-sql-vs-nosql.md` with the trade-off analysis | `BLOCKED` | Same reason as 4.10 |
| 4.12 | Final README, design doc revision, testing evidence assembled | `PENDING` | — |

**Exit criteria**

- Every performance claim is backed by a recorded number in a CSV.
- Cache invalidation correctness is proven by a test, not asserted in prose.

**Maps to:** DSA Application (15 pts), Performance Optimization (10 pts), remainder of
Documentation (10 pts).

---

## Deliverables checklist

| Deliverable | Status | Location |
|---|:---:|---|
| Database Design Document | `DONE` | `docs/01-database-design.md` |
| SQL Implementation Script | `DONE` | `db/postgres/V1-V3` |
| NoSQL Implementation Script | `DONE` | `db/mongo/01-03` |
| JavaFX Application | `WIP` | `ui/` + `view/`; builds and launches, CRUD unverified |
| Performance Report | `PENDING` | `docs/02-performance-report.md` |
| SQL vs NoSQL Comparison | `PENDING` | `docs/03-sql-vs-nosql.md` |
| README | `DONE` | `README.md` |
| Testing Evidence | `WIP` | **166 passing tests, incl. 29 live-database ITs**; real-data screenshots pending (3.13) |

---

## Change log

- **2026-11-01** — Plan created. Stack locked: PostgreSQL + MongoDB, native install.
- **2026-11-01** — Phase 1 completed. `mvn clean compile` passes; all three MongoDB
  scripts parse and their generation logic was simulated (5 005 comments, 1 500 reviews,
  zero duplicate review pairs, all materialized paths well formed). SQL scripts written
  but not executed — they need a running PostgreSQL server.
- **2026-11-01** — PostgreSQL 18 found running on **port 5433**, not the default 5432;
  `application.properties` and the README now point there.
- **2026-11-01** — Phase 2 completed. `mvn test`: 71 passing, 0 failures. DAO integration
  tests written but skipping until a database is reachable, so no SQL has yet executed
  against a live server (step 2.14 `BLOCKED`).
- **2026-11-01** — `phases.md` restructured: per-step status tables with an evidence
  column and explicit rules for when a step may be marked `DONE`.
- **2026-11-01** — Phase 3 started. Building the UI as a professional content-management
  console rather than a default-skinned JavaFX form.
- **2026-11-01** — Phase 3 UI built: design system (`app.css`), shell with sidebar
  routing, and five screens (Dashboard, Posts, Post editor, Post detail, Moderation,
  Authors, Tags). `HelloApplication` scaffold removed; the UI is built programmatically
  rather than from FXML, so an `fx:id` typo is a compile error instead of a runtime one.
  `javafx-fxml` dropped from the dependencies.
- **2026-11-01** — App launched and screen-captured for verification. Three defects found
  and fixed from the capture: a focus ring competing with the active nav state, a banner
  that printed the same advice twice, and a window restoring partly off-screen.
  `mvn test` still 71 passing. Steps 3.12/3.13 remain `BLOCKED` on a live database.
- **2026-11-01** — Phase 4 DSA work done while waiting on the databases. Added `LruCache`
  (access-order `LinkedHashMap`, O(1), synchronised), `CachedPostDao` (decorator, so the
  service layer cannot forget to invalidate), `InvertedIndex` (hash-based keyword
  lookup), `Sorting` (stable merge sort, binary search, `lowerBound`) and the `Benchmark`
  harness (warm-up, median, checksum against dead-code elimination, CSV). 66 new tests;
  suite now **137 passing, 0 failures**. Steps 4.1–4.4 and 4.8 `DONE`; 4.5–4.7 and 4.9
  `BLOCKED` on step 3.0, and 4.10/4.11 deliberately not started so the reports are
  written from measurements rather than guesses.
- **2026-11-01** — Responsive pass. The display is 1920x1080 at 150% scaling, i.e.
  1280x720 logical with a 672px work area, and the window was being created at
  1340x840 logical with a 680px minimum height: larger than the screen in both
  directions and impossible to fit. Window sizing is now derived from
  `Screen.getVisualBounds()`.
  Four real defects then found by capturing each screen and reading it:
  (1) the status banner rendered as one ellipsised line because a wrapped label
  nested in VBoxes is measured at single-line height - fixed with `USE_PREF_SIZE`;
  (2) `.text-mono` resolved to a symbol face and rendered ASCII as garbage, because
  JavaFX does not resolve a web-style font fallback list - pinned to one family;
  (3) unwrapped error labels widened their panel until the Tags screen grew a
  horizontal scrollbar - added `Styles.message()`, used across the views;
  (4) the tag filter read "all tags" against "All authors"/"All statuses".
  Filter bar and dashboard grids also made adaptive. `mvn test` still 137 passing.
  Note: the first round of screenshots was misleading - the capturing process was
  not DPI-aware, so it grabbed only the top-left corner of the window and the crop
  looked like overflow. Corrected before drawing any conclusion from it.
- **2026-11-02** — Step 3.0 done: `blogging_platform` created on PostgreSQL 18.3 (port
  5433) and MongoDB 9.0.4, all six scripts executed. Running live exposed three defects:
  (1) **Seed skew.** Every `(SELECT ... ORDER BY random() LIMIT 1)` in `V3__seed.sql` was
  uncorrelated, so PostgreSQL ran it once as an InitPlan: all 500 generated posts had one
  author, every post the same tags, all 5 000 comments sat on one post, and the review
  generator produced 1 row instead of ~1 500. Each subquery now references the outer
  row. Re-seeded: posts by 191 authors, comments on all 508 posts (max 30 per post),
  1 982 reviews. Phase 4 benchmarks on the old data would have been meaningless.
  (2) **Mongo thread order.** `path` stores ancestors only, so every root has `"/"` and
  sorting by `path` listed all roots before any reply; the detail view indented replies
  under the wrong comment. `MongoCommentDao` now reads chronologically and walks the
  tree depth-first, matching the SQL CTE, including filtering hidden comments *after*
  ordering. New IT `hiddenParentKeepsVisibleReply`.
  (3) **IT mutated the seed.** `renameAuthorUpdatesCopies` used author 77, a real seeded
  user, and renamed 26 of their seeded comments. Now uses an id outside the seeded range.
  Also: `03-seed.js` passes `NumberLong` values as strings (removes driver warnings).
  Final counts — PostgreSQL: users 207, posts 508, tags 12, post_tags 1 259,
  comments 5 008, reviews 1 982. MongoDB: comments 5 005, reviews 1 500.
  `mvn test`: **166 run, 0 failures, 0 skipped**; seed counts unchanged after the run.
- **2026-11-02** — Phase 3 done. Built `UiCrudE2E`, which drives the real application
  through every CRUD path and checks each result in the databases (13/13 passing), plus
  `FxDriver` (scene-graph lookup, dialog answering, waiting on `Async.inFlight()` rather
  than sleeps) and a dependency-free PNG writer for the captures. Reading the eight
  real-data captures found five defects no test had caught:
  (1) **Engagement figures from the wrong store.** The post list and dashboard computed
  comment and review counts with subqueries on PostgreSQL, while comments and reviews were
  served from MongoDB: "8 comments, 2.83 (6)" in the list, "141 comments, 3.25 from 40" on
  opening the post. `PostService` now fills them from the active store with two batched
  lookups per page (`countVisibleByPostIds`, `statsByPostIds`, one grouped query each in
  both stores); if that store is down, posts still list with the figures shown as unknown.
  New `EngagementBatchIT` proves batch and per-post figures agree in both stores.
  (2) **Every bar rendered full width** (rating distribution, tag usage, dashboard): a
  StackPane stretches a Region to its max width, and only pref/min were set. One shared
  `Styles.proportionBar` now bounds the fill to a fraction of the real track width.
  (3) Post detail header read "Post" instead of the title — the heading was set before the
  post loaded; added `AppShell.refreshHeading`.
  (4) Review ratings rendered as `****-`; now star glyphs.
  (5) Status badges and comment counts truncated in the post table on a 1280px screen;
  fixed-content columns now have minimum widths.
