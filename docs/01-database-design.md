# Database Design Document — Smart Blogging Platform

**Version:** 1.1 · **Date:** 2026-11-02 · **Phase:** 1, revised after Phase 4 measurements

---

## 1. Design Decision: Polyglot Persistence

The platform uses **two stores**, each where it is strongest:

| Store | Holds | Why |
|-------|-------|-----|
| **PostgreSQL 18** | `users`, `posts`, `tags`, `post_tags`, `comments`, `reviews` | Structured, highly relational data with hard integrity rules. Authorship, tagging and publication state are all constraint problems, and a relational engine enforces them for free. |
| **MongoDB 9** | `comments`, `reviews` collections | The same two entities, modelled as documents. Comment threads are variable-depth and their metadata is open-ended (edit history, reactions, moderation flags), exactly the shape that forces either a sparse table or an EAV side table in SQL. |

### Why `comments` and `reviews` exist in *both* stores

This is deliberate, not accidental duplication. Epic 4.2 asks for a NoSQL design for
unstructured data **and** an SQL/NoSQL comparison. Implementing the identical entity in
both engines, behind one `CommentDao` interface, turns that comparison from an opinion
into a measurement: Phase 4 runs the same workloads (threaded read, deep insert, keyword
search) against both and records real timings.

The relational version is the system of record for referential integrity; the document
version is the system of record for flexible, nested, write-heavy discussion data. The
active store is selectable at runtime via `app.comments.store` in
`application.properties`.

---

## 2. Conceptual Model

Entities and the business rules that bind them:

- A **User** writes many **Posts**. A post has exactly one author.
- A **Post** carries many **Comments**. A comment belongs to exactly one post.
- A **Comment** may reply to another comment on the same post (self-referencing,
  arbitrary depth).
- A **User** writes many **Comments**. A comment has one author.
- A **Post** has many **Tags**; a **Tag** labels many **Posts** (M:N, resolved by the
  `post_tags` associative entity).
- A **User** may leave at most **one Review** per **Post** (rating 1-5 plus prose).

```mermaid
erDiagram
    USERS ||--o{ POSTS     : "writes"
    USERS ||--o{ COMMENTS  : "authors"
    USERS ||--o{ REVIEWS   : "submits"
    POSTS ||--o{ COMMENTS  : "receives"
    POSTS ||--o{ REVIEWS   : "is rated by"
    POSTS ||--o{ POST_TAGS : ""
    TAGS  ||--o{ POST_TAGS : ""
    COMMENTS ||--o{ COMMENTS : "replies to"

    USERS {
        bigint      user_id PK
        varchar     username UK
        varchar     email UK
        varchar     password_hash
        varchar     display_name
        text        bio
        varchar     role
        boolean     is_active
        timestamptz created_at
        timestamptz updated_at
    }
    POSTS {
        bigint      post_id PK
        bigint      author_id FK
        varchar     title
        varchar     slug UK
        text        body
        varchar     excerpt
        varchar     status
        integer     view_count
        timestamptz published_at
        timestamptz created_at
        timestamptz updated_at
    }
    TAGS {
        integer     tag_id PK
        varchar     name UK
        varchar     slug UK
        timestamptz created_at
    }
    POST_TAGS {
        bigint      post_id PK
        integer     tag_id PK
        timestamptz tagged_at
    }
    COMMENTS {
        bigint      comment_id PK
        bigint      post_id FK
        bigint      author_id FK
        bigint      parent_comment_id FK
        text        body
        varchar     status
        timestamptz created_at
        timestamptz updated_at
    }
    REVIEWS {
        bigint      review_id PK
        bigint      post_id FK
        bigint      reviewer_id FK
        smallint    rating
        varchar     headline
        text        body
        timestamptz created_at
        timestamptz updated_at
    }
```

---

## 3. Logical Model

### 3.1 `users`

| Attribute | Type | Constraints |
|---|---|---|
| user_id | BIGSERIAL | **PK** |
| username | VARCHAR(50) | NOT NULL, **UNIQUE**, CHECK length >= 3 |
| email | VARCHAR(255) | NOT NULL, **UNIQUE**, CHECK format |
| password_hash | VARCHAR(255) | NOT NULL |
| display_name | VARCHAR(100) | NOT NULL |
| bio | TEXT | NULL |
| role | VARCHAR(20) | NOT NULL, CHECK IN (ADMIN, AUTHOR, READER), DEFAULT AUTHOR |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() |

### 3.2 `posts`

| Attribute | Type | Constraints |
|---|---|---|
| post_id | BIGSERIAL | **PK** |
| author_id | BIGINT | **FK** to users(user_id) ON DELETE CASCADE, NOT NULL |
| title | VARCHAR(255) | NOT NULL, CHECK not blank |
| slug | VARCHAR(280) | NOT NULL, **UNIQUE** |
| body | TEXT | NOT NULL |
| excerpt | VARCHAR(500) | NULL |
| status | VARCHAR(20) | NOT NULL, CHECK IN (DRAFT, PUBLISHED, ARCHIVED), DEFAULT DRAFT |
| view_count | INTEGER | NOT NULL, DEFAULT 0, CHECK >= 0 |
| published_at | TIMESTAMPTZ | NULL, forced NOT NULL when status = PUBLISHED |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() |

### 3.3 `tags`

| Attribute | Type | Constraints |
|---|---|---|
| tag_id | SERIAL | **PK** |
| name | VARCHAR(50) | NOT NULL, **UNIQUE**, lower-case enforced by CHECK |
| slug | VARCHAR(60) | NOT NULL, **UNIQUE** |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() |

### 3.4 `post_tags` (associative)

| Attribute | Type | Constraints |
|---|---|---|
| post_id | BIGINT | **PK** (composite), **FK** to posts ON DELETE CASCADE |
| tag_id | INTEGER | **PK** (composite), **FK** to tags ON DELETE CASCADE |
| tagged_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() |

### 3.5 `comments`

| Attribute | Type | Constraints |
|---|---|---|
| comment_id | BIGSERIAL | **PK** |
| post_id | BIGINT | **FK** to posts ON DELETE CASCADE, NOT NULL |
| author_id | BIGINT | **FK** to users ON DELETE SET NULL, NULL (preserves orphaned threads) |
| parent_comment_id | BIGINT | **FK** to comments ON DELETE CASCADE, NULL |
| body | TEXT | NOT NULL, CHECK not blank |
| status | VARCHAR(20) | NOT NULL, CHECK IN (VISIBLE, HIDDEN, FLAGGED), DEFAULT VISIBLE |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() |

### 3.6 `reviews`

| Attribute | Type | Constraints |
|---|---|---|
| review_id | BIGSERIAL | **PK** |
| post_id | BIGINT | **FK** to posts ON DELETE CASCADE, NOT NULL |
| reviewer_id | BIGINT | **FK** to users ON DELETE CASCADE, NOT NULL |
| rating | SMALLINT | NOT NULL, CHECK BETWEEN 1 AND 5 |
| headline | VARCHAR(150) | NULL |
| body | TEXT | NULL |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() |
| | | **UNIQUE (post_id, reviewer_id)** - one review per user per post |

---

## 4. Normalization to 3NF

**1NF** - every column holds a single atomic value. The M:N relationship between posts
and tags is *not* stored as a comma-separated `tags` column on `posts`; it is decomposed
into `post_tags`. Comment threading is a foreign key, not a nested list.

**2NF** - no partial dependency on a composite key. The only composite key in the schema
is `post_tags(post_id, tag_id)`, and its sole non-key attribute `tagged_at` depends on
the whole key (the moment *that tag* was applied to *that post*), not on either half.

**3NF** - no transitive dependencies. `posts` stores `author_id` only; the author's
`username`, `email` and `display_name` live in `users` and are reached by join.
`tags.name` is stored once and referenced by id, so renaming a tag is a single-row
update. `reviews` stores `rating` but no derived `average_rating` is kept on `posts` -
that is computed by aggregate query.

### Documented exceptions

Two columns are denormalized on purpose. Both are deliberate performance trade-offs
rather than oversights:

1. **`posts.view_count`** - derivable from a view-event log, but no such log exists in
   this scope, and an increment-on-read counter is O(1) where `COUNT(*)` over an event
   table is O(n). It is updated through a single service method so it cannot drift.
2. **`posts.excerpt`** - derivable from `body`, but precomputing it stops the list screen
   pulling full post bodies over the wire for every row. Regenerated whenever `body`
   changes.

`posts.slug` and `tags.slug` are *not* denormalization: they are independent, immutable
external identifiers (candidate keys), not copies of `title`/`name`. A post title can be
edited without breaking its public URL.

---

## 5. Index Strategy

Indexes live in `V2__indexes.sql`, separate from the schema, so Phase 4 can benchmark the
database with and without them by simply choosing whether to run that file.

| Index | Table / Expression | Serves |
|---|---|---|
| `idx_posts_author_id` | posts(author_id) | "posts by author" filter; the FK cascade on user deletion |
| `idx_posts_status_published` | posts(status, published_at DESC) | the default feed query - composite, so one index covers filter **and** sort |
| `idx_posts_title_lower` | posts(LOWER(title)) | case-insensitive exact title lookup (US 3.1) |
| `idx_posts_title_trgm` | GIN posts(title gin_trgm_ops) | `ILIKE '%term%'` substring search, which a B-tree cannot serve |
| `idx_posts_search_tsv` | GIN posts(setweight(to_tsvector(title),'A') \|\| setweight(to_tsvector(body),'B')) | full-text keyword search across title and body, title weighted higher |
| `idx_posts_created_at` | posts(created_at DESC) | newest-first listing |
| `idx_posts_views_published` | posts(view_count DESC) WHERE status = 'PUBLISHED' | partial; "most viewed", which only ever lists published posts |
| `idx_tags_name_lower` | tags(LOWER(name)) | tag lookup by name, case-insensitive |
| `idx_tags_name_trgm` | GIN tags(name gin_trgm_ops) | tag autocomplete |
| `idx_post_tags_tag_id` | post_tags(tag_id, post_id) | reverse lookup "all posts with this tag" - the composite PK only covers post-first access |
| `idx_comments_post_created` | comments(post_id, created_at) | load a post's comment thread in order; per-post comment counts |
| `idx_comments_parent` | comments(parent_comment_id) WHERE NOT NULL | partial; reply resolution, skipping the top-level majority |
| `idx_comments_author` | comments(author_id) | "my comments" |
| `idx_comments_moderation` | comments(created_at DESC) WHERE status <> 'VISIBLE' | partial; the moderation queue, about 2% of rows, read newest-first without a sort |
| `idx_comments_body_trgm` | GIN comments(body gin_trgm_ops) | keyword search in comments, `ILIKE '%term%'` (added in Phase 4) |
| `idx_reviews_post_rating` | reviews(post_id, rating) | rating count and average per post, answered index-only |
| `idx_reviews_reviewer` | reviews(reviewer_id, created_at DESC) | "my reviews", newest first |

**An expression index only serves the identical expression.** PostgreSQL matches an
expression index structurally: a query must repeat the indexed expression exactly, not
an equivalent one. Phase 4 found the post search written as
`to_tsvector('english', title || ' ' || body)` while the index above was built on the
weighted, `coalesce`d form. Both find the same posts; only one can use the index. The
query now repeats the indexed expression - see `docs/02-performance-report.md` §3.

**Rationale for the composite order.** `(status, published_at DESC)` is ordered
equality-column-first: the feed always filters `status = 'PUBLISHED'` and then sorts by
date, so PostgreSQL can seek to the status prefix and read the already-ordered range,
skipping a sort node entirely. Reversed, the index could not serve the filter.

**Indexes deliberately *not* created.** None on `posts.body` alone (always searched
through the tsvector), and none on `users.role`, `posts.status` or `users.is_active`
alone - these are low-cardinality columns where a sequential scan beats an index because
any single value matches a large fraction of the rows.

---

## 6. Referential Integrity Rules

| Relationship | On delete | Reasoning |
|---|---|---|
| posts to users | CASCADE | Removing an author removes the content they authored. |
| comments to posts | CASCADE | A comment has no meaning without its post. |
| comments to users | SET NULL | A deleted account must not silently erase a conversation others replied to; the comment survives as "[deleted user]". |
| comments to comments | CASCADE | Deleting a parent removes its reply subtree. |
| post_tags to posts / tags | CASCADE | Pure association rows. |
| reviews to posts / users | CASCADE | A review is meaningless without either side. |

`updated_at` is maintained by a trigger (`set_updated_at`) rather than by application
code, so it stays correct no matter which client writes.

---

## 7. MongoDB Document Design

### 7.1 `comments`

Modelled **flat with a materialized path**, not as deeply nested subdocuments. Nesting
replies inside their parent makes the 16 MB document limit a real risk on a popular post,
and turns "update one reply" into a whole-document rewrite. The materialized path
(`/661f.../889a.../`) gives subtree reads through a single indexed prefix match while
keeping each comment an independently updatable document.

```js
{
  _id:         ObjectId,
  postId:      NumberLong(42),      // references PostgreSQL posts.post_id
  authorId:    NumberLong(7),
  authorName:  "ada_l",             // denormalized: comment lists never join
  parentId:    ObjectId | null,
  path:        "/661f.../",         // ancestor ids, root-first; "/" at top level
  depth:       1,
  body:        "...",
  status:      "VISIBLE",
  reactions:   { like: 12, insightful: 3 },        // open-ended, schemaless by design
  editHistory: [ { body: "...", editedAt: ISODate } ],
  createdAt:   ISODate,
  updatedAt:   ISODate
}
```

`authorName` is denormalized on purpose: there is no cross-store join available, and a
comment list must not fan out into N PostgreSQL lookups. The service layer refreshes it
on profile rename.

### 7.2 `reviews`

```js
{
  _id:          ObjectId,
  postId:       NumberLong(42),
  reviewerId:   NumberLong(7),
  reviewerName: "ada_l",
  rating:       5,                                  // 1-5, validated
  headline:     "...",
  body:         "...",
  criteria:     { clarity: 5, depth: 4, accuracy: 5 },   // optional, varies per review
  helpfulVotes: 9,
  createdAt:    ISODate,
  updatedAt:    ISODate
}
```

`criteria` is the clearest argument for the document model: an optional, per-review
variable set of sub-scores. In SQL that is either a wide sparse table or an EAV side
table; in a document it is just a field.

### 7.3 Mongo indexes

| Index | Serves |
|---|---|
| `{ postId: 1, createdAt: 1 }` | load a thread in order |
| `{ postId: 1, path: 1 }` | subtree / reply-chain reads via prefix match |
| `{ parentId: 1 }` | direct children |
| `{ authorId: 1, createdAt: -1 }` | a user's comment history |
| `{ body: "text" }` | keyword search inside comments |
| `{ postId: 1, reviewerId: 1 }` **unique** | mirrors the SQL one-review-per-user rule |
| `{ postId: 1, rating: -1 }` | rating distribution per post |

Schema validation is enforced with `$jsonSchema` at `validationLevel: "strict"`, so the
"schemaless" store still rejects malformed data: flexibility where it is wanted
(`reactions`, `criteria`), rigidity where it is needed (`postId`, `rating`).

---

## 8. Capacity and Growth Notes

- `posts.post_id` and `comments.comment_id` are `BIGSERIAL`: comment volume is the
  fastest-growing dimension, and a 32-bit ceiling is a foreseeable defect.
- `tags.tag_id` stays `SERIAL` - a tag vocabulary is bounded in the thousands.
- All timestamps are `TIMESTAMPTZ`, never `TIMESTAMP`: readers sit in multiple timezones
  and a naive timestamp silently loses that.
- The app database starts with only the admin account; all other data is entered
  through the app, so it is far too small for indexes to matter. Phase 4 measured on a separate database built by `db/bench/scale.sql`
  (100 000 posts, 630 000 comments, 260 000 reviews), where the index decisions in §5
  show up as 11× to 1,300× differences; see `docs/02-performance-report.md`.

---

## 9. Items Resolved in Phase 4

- **Keyset pagination.** Measured against OFFSET: equal on page one, 398× faster at row
  50 000. The post list keeps OFFSET, because it offers page numbers and totals that
  keyset cannot, and the demo's deepest page costs about 1 ms. Keyset is the
  recommendation for next-only feeds. Report §6.
- **Materialized view for rating aggregates: not needed.** The live aggregate for a
  page of 20 posts takes 0.70 ms as an index-only scan on `idx_reviews_post_rating`.
  A materialized view would add a refresh problem to save less than a millisecond.
  Report §4.1.
- **Full-text weighting.** The weighted index existed, but the query never used it
  (report §3, defect 1). Fixed, so title and body hits are now both served by
  the index.
- **Engagement figures and the comment store.** Not an open item before Phase 3. The
  post list's comment and review counts now come from whichever store
  `app.comments.store` selects, through batched lookups, instead of from PostgreSQL
  subqueries. `docs/03-sql-vs-nosql.md` compares the two stores.
