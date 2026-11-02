# SQL vs NoSQL for Comments and Reviews

**Version:** 1.0 · **Date:** 2026-11-02 · **Phase:** 4 · **Covers:** Epic 4, US 4.2

Comments and reviews are implemented twice: as PostgreSQL tables and as MongoDB
collections, behind one `CommentDao` and one `ReviewDao` interface.
`app.comments.store` chooses which one the application uses. This document justifies
the document design (US 4.2) and then compares the two on measurements rather than
reputation.

All timings come from [`benchmarks/sql-vs-nosql.csv`](benchmarks/sql-vs-nosql.csv) and
[`benchmarks/storage.csv`](benchmarks/storage.csv). Both stores held **identical data**:
630 000 comments in threads three levels deep and 259 930 reviews, copied row for row
from PostgreSQL into MongoDB. Method and limits are in the
[performance report](02-performance-report.md) §2.

---

## 1. Why a document model for this data

Posts, users and tags stay relational without debate. Their rules are integrity rules
(one author per post, unique slugs, one review per user per post), and a relational
engine enforces those for free.

Comments and reviews are where a document store has a real case:

| Property of the data | Relational cost | Document answer |
|---|---|---|
| **Variable-depth threads** | Adjacency list; reading a subtree is a recursive CTE, one step per level | Materialized `path` (`/rootId/childId/`); a subtree is one indexed prefix match |
| **Open-ended metadata** – reactions (`like`, `insightful`…), review sub-scores (`clarity`, `depth`…) whose keys vary per document | A sparse column per key, or an entity-attribute-value side table | A nested object; a new key needs no migration |
| **Edit history** | A second table and a join | An array field, `$push`ed on edit |
| **Read pattern** – a thread is always read whole, by post | A join to `users` for every author name | Author name stored on the comment; no join |

### 1.1 Schema side by side

```
PostgreSQL comments                    MongoDB comments
-------------------                    ----------------
comment_id        BIGSERIAL PK         _id         ObjectId
post_id           FK -> posts          postId      long     (no FK possible)
author_id         FK -> users          authorId    long
                                       authorName  string   (denormalized)
parent_comment_id FK -> comments       parentId    ObjectId | null
                                       path        "/<id>/<id>/"
                                       depth       int
body, status, created_at, updated_at   body, status, createdAt, updatedAt
(no column)                            reactions   { kind: count, ... }
(no column)                            editHistory [ { body, editedAt } ]
```

The collection is still **not schemaless**. A `$jsonSchema` validator at
`validationLevel: "strict"` enforces types, the `path` pattern, the status enum and the
rating range. Every one of the 890 000 copied documents passed it. The flexibility is
confined to the fields where it is wanted (`reactions`, `criteria`, `editHistory`).

Replies are separate documents, not nested inside their parent. Nesting would risk
MongoDB's 16 MB document limit on a popular post, and editing one reply would rewrite
the whole thread.

---

## 2. Measured comparison

Both stores with their full index sets (`V2__indexes.sql`, `02-indexes.js`). Median of
51 iterations (11 for keyword search, 21 for rename).

| Workload | PostgreSQL | MongoDB | Faster |
|---|---:|---:|---|
| Read a post's whole thread, in thread order | 5.43 ms | 4.70 ms | MongoDB, 1.2× |
| Direct replies to a comment | 0.63 ms | 3.06 ms | PostgreSQL, 4.9× |
| Subtree, three levels | 1.84 ms | 6.27 ms | PostgreSQL, 3.4× |
| Keyword search in comment bodies | 25.7 ms | 7.05 ms | MongoDB, 3.6× |
| Visible-comment counts for a page of 20 posts | 1.06 ms | 4.95 ms | PostgreSQL, 4.7× |
| Review count and average for 20 posts | 0.65 ms | 4.69 ms | PostgreSQL, 7.2× |
| Moderation queue, newest 50 | 1.66 ms | 3.19 ms | PostgreSQL, 1.9× |
| Insert a comment | 1.71 ms | 3.26 ms | PostgreSQL, 1.9× |
| Delete a comment and its replies | 1.00 ms | 6.02 ms | PostgreSQL, 6.0× |
| Rename an author everywhere | 0.004 ms | 14.9 ms | PostgreSQL (nothing to do) |

### 2.1 Reading the table

- **Neither store wins every workload.** Thread reads, the core read of the feature,
  are within 1.2× of each other. MongoDB reads one index range and needs no join;
  PostgreSQL runs a recursive CTE and joins `users` for names, yet still finishes close
  behind.
- **The materialized path did not beat the recursive CTE on subtrees.** That is the
  opposite of the usual claim, so it is worth saying why. With three levels, the CTE
  recurses three times, and each step is an index probe on `idx_comments_parent`. That
  is cheap. The path's advantage grows with depth; at three levels it does not show.
  What remains is MongoDB's higher per-query overhead in this setup: the BSON
  round trip and driver decoding, visible as a 3 ms floor on even a single-key lookup
  (`replies_to_comment`).
- **Aggregates favour PostgreSQL clearly, and not because MongoDB reads more.**
  `explain` shows the review pipeline is covered by `{postId, rating}`: 76 index keys,
  **0 documents examined**, the same work as PostgreSQL's index-only scan. The gap is
  the fixed cost of an aggregation pipeline round trip, not I/O. The comment count
  does fetch documents (126 for a page), because `status` is not in
  `idx_comments_post_created`. Adding it would make that pipeline covered too.
- **Keyword search favours MongoDB, with a catch.** Its text index is a stemmed
  inverted index of whole words. PostgreSQL's trigram index answers
  `ILIKE '%term%'`, which also finds partial words (`cach` inside `caching`). MongoDB's
  is faster *and answers a different question*. MongoDB also allows only **one** text
  index per collection.
- **Writes favour PostgreSQL here,** including deletes, where MongoDB has no
  `ON DELETE CASCADE` and must remove the subtree with an explicit prefix-matched
  `deleteMany`.
- **The rename row is the cost of denormalization.** PostgreSQL stores a name once
  and reads it by join, so a rename touches one row. MongoDB copies the name onto every
  comment, so renaming a user rewrites each one: about 126 documents per user here,
  14.9 ms, growing with that user's activity.

### 2.2 Storage

| | PostgreSQL data | PostgreSQL indexes | MongoDB data | MongoDB indexes |
|---|---:|---:|---:|---:|
| comments (630 000) | 91.5 MB | 94.2 MB (6) | 63.8 MB | 84.6 MB (7) |
| reviews (259 930) | 27.1 MB | 25.6 MB (4) | 12.6 MB | 11.0 MB (4) |

MongoDB's data is smaller on disk because WiredTiger compresses it (snappy by default);
PostgreSQL's heap is uncompressed. On the index side, PostgreSQL's trigram index on
comment bodies (added in Phase 4) accounts for over half of the 94.2 MB. Both sizes
move with write history: MongoDB's comment data measured 44.1 MB before the benchmark's
inserts and deletes and 63.8 MB after, because WiredTiger keeps freed space for reuse
rather than returning it.

---

## 3. Integrity: what each store guarantees

| Rule | PostgreSQL | MongoDB |
|---|---|---|
| A comment belongs to an existing post | `FOREIGN KEY` | **Nothing.** `postId` is a number. The service layer deletes a post's comments and reviews before the post itself (`PostListView` → `CommentService.deleteForPost`) |
| Deleting a comment removes its replies | `ON DELETE CASCADE` | Explicit prefix `deleteMany` in `MongoCommentDao.deleteWithReplies` |
| One review per user per post | `UNIQUE` constraint | Unique index `uq_reviews_post_reviewer` |
| Rating between 1 and 5 | `CHECK` | `$jsonSchema` `minimum`/`maximum` |
| Author name is current | Joined at read time | Copied, kept current by `renameAuthor` across all copies |

Everything MongoDB guarantees here, it guarantees through a unique index, a validator,
or application code. The application code is the weak point: anything that writes to
the collection without going through the DAO bypasses it. The end-to-end UI test
(`UiCrudE2E`, step 12) proves the cross-store delete works. The relational store needed
no such test, because it cannot get this wrong.

---

## 4. What MongoDB made easy to get wrong

The first benchmark run found three MongoDB defects. Each returned correct results,
and each looked natural in code:

| Mistake | Effect | Before | After |
|---|---|---:|---:|
| Subtree filter on `path` without `postId` | Index `{postId, path}` unusable; scanned 630 000 documents | 2.12 s | 6.27 ms |
| Prefix regex built with `Pattern.quote` (`^\Q…\E`) | MongoDB cannot derive index bounds from it | (part of the above) | |
| Moderation filter `status: {$ne: 'VISIBLE'}` | Cannot prove it implies the partial index's filter; collection scan | 438.4 ms | 3.19 ms |

PostgreSQL had its own: an expression index the query did not match exactly
(performance report §3, defect 1). The common lesson is that **an index is only used
when the query is written to fit it**, and only measuring shows when it is not.

---

## 5. Conclusion

| Criterion | Better fit |
|---|---|
| Schema flexibility (reactions, sub-scores, edit history) | **MongoDB** |
| Whole-thread read | Even (1.2×) |
| Aggregates, counts, ratings | **PostgreSQL** (4.7–7.2×) |
| Writes and deletes | **PostgreSQL** (1.9–6.0×) |
| Whole-word keyword search | **MongoDB** (3.6×) |
| Substring search | **PostgreSQL** (MongoDB cannot do it with its text index) |
| Referential integrity | **PostgreSQL**, enforced by the engine rather than by convention |

At this data volume and this nesting depth, **MongoDB's case for comments and reviews
rests on schema flexibility, not on speed.** PostgreSQL was as fast or faster on
eight of the ten workloads, and it enforces integrity that MongoDB leaves to the
application.

The recommendation therefore depends on which matters more for the product:

- If reactions, review sub-scores and edit history are core and still changing, the
  document model earns its keep: those fields need no migrations, and thread reads are
  as fast. The cost is the application-enforced integrity listed in §3.
- If those fields are stable, PostgreSQL alone is simpler and at least as fast. The
  open-ended fields would become a `jsonb` column: indexable with GIN, without a second
  database to operate.

The application supports both today: set `app.comments.store` in
`application.properties` (`MONGO` is the default).
