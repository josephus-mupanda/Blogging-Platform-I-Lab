-- =====================================================================
-- Smart Blogging Platform - Seed Data (PostgreSQL)
-- Phase 1
--
-- Run as:  psql -U postgres -d blogging_platform -f db/postgres/V3__seed.sql
--
-- Two layers of data:
--   1. A small hand-written set that is readable in the UI during demos.
--   2. A generated bulk set (~500 posts, ~5 000 comments) so that the
--      Phase 4 index benchmarks measure something real. A 20-row table is
--      index-immune: the planner will seq-scan it no matter what.
--
-- setseed() makes the generated data reproducible across machines, so
-- benchmark numbers are comparable.
--
-- Every "pick a random row" subquery below references the outer row
-- (`WHERE g IS NOT NULL`, `WHERE p.post_id IS NOT NULL`). Without that,
-- the subquery is uncorrelated, PostgreSQL evaluates it once as an
-- InitPlan, and every generated row gets the *same* random pick: all
-- posts by one author, all comments on one post, one review in total.
-- =====================================================================

BEGIN;

SELECT setseed(0.42);

TRUNCATE reviews, comments, post_tags, tags, posts, users RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------
-- 1. Hand-written demo data
-- ---------------------------------------------------------------------

-- Password hashes are placeholders for BCrypt output; authentication is
-- out of scope for this phase.
INSERT INTO users (username, email, password_hash, display_name, bio, role) VALUES
    ('admin',    'admin@blogplatform.io',  '$2a$10$seedplaceholderhashadmin00000000000000000000000000', 'Site Admin',     'Keeps the lights on.',                       'ADMIN'),
    ('ada_l',    'ada@blogplatform.io',    '$2a$10$seedplaceholderhashada0000000000000000000000000000', 'Ada Lovelace',   'Writes about algorithms and analytical engines.', 'AUTHOR'),
    ('grace_h',  'grace@blogplatform.io',  '$2a$10$seedplaceholderhashgrace00000000000000000000000000', 'Grace Hopper',   'Compilers, debugging and nanoseconds.',       'AUTHOR'),
    ('linus_t',  'linus@blogplatform.io',  '$2a$10$seedplaceholderhashlinus00000000000000000000000000', 'Linus T.',       'Kernels and version control.',                'AUTHOR'),
    ('barbara_l','barbara@blogplatform.io','$2a$10$seedplaceholderhashbarbara000000000000000000000000', 'Barbara Liskov', 'Abstraction, substitution, distributed systems.', 'AUTHOR'),
    ('reader01', 'reader01@blogplatform.io','$2a$10$seedplaceholderhashreader1000000000000000000000000','Jay Reader',     NULL,                                          'READER'),
    ('reader02', 'reader02@blogplatform.io','$2a$10$seedplaceholderhashreader2000000000000000000000000','Sam Reader',     NULL,                                          'READER');

INSERT INTO tags (name, slug) VALUES
    ('java',        'java'),
    ('postgresql',  'postgresql'),
    ('mongodb',     'mongodb'),
    ('javafx',      'javafx'),
    ('algorithms',  'algorithms'),
    ('data-structures', 'data-structures'),
    ('performance', 'performance'),
    ('indexing',    'indexing'),
    ('caching',     'caching'),
    ('architecture','architecture'),
    ('testing',     'testing'),
    ('sql',         'sql');

INSERT INTO posts (author_id, title, slug, body, excerpt, status, view_count, published_at) VALUES
    (2, 'Designing a Blogging Schema That Survives Growth',
        'designing-a-blogging-schema-that-survives-growth',
        'Normalization is not an academic exercise. The moment a tag name is duplicated across ten thousand rows, renaming it becomes a migration instead of an update. This post walks through third normal form applied to a blogging domain, and the two places where breaking it is the right call.',
        'Third normal form applied to a blogging domain, and when to break it.',
        'PUBLISHED', 412, now() - INTERVAL '30 days'),
    (3, 'B-tree or GIN? Choosing an Index for Text Search',
        'btree-or-gin-choosing-an-index-for-text-search',
        'A B-tree cannot answer ILIKE with a leading wildcard. That single fact drives most of the index decisions in a content platform. Here is how trigram and full-text indexes differ in cost, size and the shape of query they actually accelerate.',
        'Why a leading wildcard defeats a B-tree, and what to use instead.',
        'PUBLISHED', 879, now() - INTERVAL '24 days'),
    (4, 'Caching Without Lying to Your Users',
        'caching-without-lying-to-your-users',
        'An LRU cache is ten lines on top of LinkedHashMap. Invalidating it correctly is the entire problem. This post covers write-through, write-around and the stale-read bug that only appears under concurrency.',
        'The easy part is the cache. The hard part is invalidation.',
        'PUBLISHED', 655, now() - INTERVAL '18 days'),
    (5, 'Threaded Comments: Adjacency List vs Materialized Path',
        'threaded-comments-adjacency-list-vs-materialized-path',
        'Recursive CTEs make adjacency lists workable in PostgreSQL, but reading a deep subtree still costs a recursion per level. A materialized path turns that into one indexed prefix match. The trade-off is write amplification on moves.',
        'Two ways to store a comment tree, and what each one costs.',
        'PUBLISHED', 1240, now() - INTERVAL '12 days'),
    (2, 'Parameterized Queries Are Not Optional',
        'parameterized-queries-are-not-optional',
        'String concatenation into SQL is the oldest vulnerability still shipping in new code. PreparedStatement is not merely safer, it is usually faster, because the plan can be reused. There is no trade-off here to weigh.',
        'PreparedStatement is safer and usually faster. There is no trade-off.',
        'PUBLISHED', 533, now() - INTERVAL '6 days'),
    (3, 'Measuring Before Optimizing: A JDBC Benchmark Harness',
        'measuring-before-optimizing-a-jdbc-benchmark-harness',
        'A benchmark without a warm-up measures the JIT compiler, not your query. A benchmark reporting a mean measures its own outliers. This is a small harness that reports medians over warmed iterations, and the mistakes it was built to avoid.',
        'Warm up, repeat, report the median. Everything else is noise.',
        'DRAFT', 0, NULL),
    (5, 'Keyset Pagination and the Cost of OFFSET 10000',
        'keyset-pagination-and-the-cost-of-offset-10000',
        'OFFSET does not skip rows cheaply; the database still produces and discards them. At page five hundred that is half a million discarded rows per request. Keyset pagination carries the last seen sort key instead, and stays flat.',
        'OFFSET gets slower the deeper you page. Keyset does not.',
        'PUBLISHED', 318, now() - INTERVAL '2 days'),
    (4, 'An Archived Note on Early Schema Mistakes',
        'an-archived-note-on-early-schema-mistakes',
        'Kept for the record: the first version of this schema stored tags as a comma-separated column. Everything that went wrong afterwards followed from that one decision.',
        'Kept for the record: what a comma-separated tag column cost us.',
        'ARCHIVED', 96, now() - INTERVAL '90 days');

INSERT INTO post_tags (post_id, tag_id) VALUES
    (1, 2), (1, 12), (1, 10),
    (2, 2), (2, 8),  (2, 7),
    (3, 1), (3, 9),  (3, 6),
    (4, 3), (4, 6),  (4, 2),
    (5, 1), (5, 12), (5, 2),
    (6, 1), (6, 7),  (6, 11),
    (7, 2), (7, 7),  (7, 8),
    (8, 2), (8, 10);

-- A readable thread: two roots, one of them with nested replies.
INSERT INTO comments (post_id, author_id, parent_comment_id, body) VALUES
    (1, 6, NULL, 'The view_count exception makes sense, but how do you stop it drifting when two requests increment concurrently?'),
    (1, 2, 1,    'Single service method doing UPDATE ... SET view_count = view_count + 1. The read-modify-write never leaves the database, so there is nothing to race.'),
    (1, 7, 2,    'That is the part I always get wrong in application code. Good catch.'),
    (1, 7, NULL, 'Would love a follow-up on the excerpt regeneration trigger.'),
    (2, 6, NULL, 'Does the trigram index get large relative to the table?'),
    (2, 3, 5,    'Noticeably, yes. On this dataset it is roughly a third of the table size. Worth it for the ILIKE case, not worth it if you only ever do prefix matches.'),
    (4, 7, NULL, 'Materialized path plus a prefix index is such an underrated pattern.'),
    (4, 5, 7,    'It is. The write amplification only bites if you actually move subtrees, which most comment systems never do.');

INSERT INTO reviews (post_id, reviewer_id, rating, headline, body) VALUES
    (1, 6, 5, 'Clearest explanation of 3NF I have read', 'The worked example of the tag column made it click.'),
    (1, 7, 4, 'Solid, wanted more on denormalization',   'The two exceptions are well argued but brief.'),
    (2, 6, 5, 'Answered exactly the question I had',      NULL),
    (2, 7, 5, 'Saved me a day of guessing',               'The size comparison at the end is the useful part.'),
    (4, 6, 4, 'Good trade-off analysis',                  NULL),
    (5, 7, 5, 'Agreed, there is no trade-off',            'Nothing to argue with here.'),
    (7, 6, 4, 'Convinced me to switch',                   'The OFFSET 10000 plan output is damning.');

-- ---------------------------------------------------------------------
-- 2. Generated bulk data for benchmarking
-- ---------------------------------------------------------------------

-- 200 additional authors.
INSERT INTO users (username, email, password_hash, display_name, role)
SELECT 'user_' || g,
       'user_' || g || '@blogplatform.io',
       '$2a$10$seedplaceholderhashbulk' || lpad(g::text, 6, '0') || '00000000000000000',
       'Bulk User ' || g,
       CASE WHEN g % 5 = 0 THEN 'READER' ELSE 'AUTHOR' END
FROM generate_series(1, 200) AS g;

-- ~500 posts spread over two years, 80% published.
INSERT INTO posts (author_id, title, slug, body, excerpt, status, view_count, published_at, created_at)
SELECT author_id,
       title,
       'generated-post-' || g,
       body,
       left(body, 160),
       status,
       floor(random() * 2000)::int,
       CASE WHEN status = 'PUBLISHED' THEN created_at ELSE NULL END,
       created_at
FROM (
    SELECT g,
           (SELECT user_id FROM users WHERE g IS NOT NULL
             ORDER BY random() LIMIT 1)                         AS author_id,
           (ARRAY['Understanding','Debugging','Scaling','Refactoring','Benchmarking',
                  'Rethinking','Profiling','Simplifying'])[1 + (g % 8)]
             || ' ' ||
           (ARRAY['Connection Pools','Recursive Queries','Hash Indexes','Query Plans',
                  'Cache Eviction','Batch Inserts','Transaction Isolation','Merge Sort',
                  'Foreign Keys','Document Schemas'])[1 + (g % 10)]
             || ' in Practice, Part ' || g                      AS title,
           'Generated benchmark content for post ' || g || '. ' ||
           repeat(
             (ARRAY['Index selectivity determines whether the planner bothers. ',
                    'A sequential scan is not automatically the wrong choice. ',
                    'Measure the median, never the mean, across warmed iterations. ',
                    'Cache invalidation is the part that actually fails in production. ',
                    'Referential integrity enforced in the database cannot be bypassed by a buggy client. '])[1 + (g % 5)],
             8)                                                 AS body,
           CASE WHEN g % 10 < 8 THEN 'PUBLISHED'
                WHEN g % 10 = 8 THEN 'DRAFT'
                ELSE 'ARCHIVED' END                             AS status,
           now() - (random() * INTERVAL '730 days')             AS created_at
    FROM generate_series(1, 500) AS g
) seeded;

-- 1-4 tags per generated post.
INSERT INTO post_tags (post_id, tag_id)
SELECT DISTINCT p.post_id, t.tag_id
FROM posts p
CROSS JOIN LATERAL (
    SELECT tag_id FROM tags WHERE p.post_id IS NOT NULL
    ORDER BY random() LIMIT 1 + floor(random() * 4)::int
) t
WHERE p.post_id > 8;

-- ~4 000 top-level comments.
INSERT INTO comments (post_id, author_id, parent_comment_id, body, status, created_at)
SELECT p.post_id,
       (SELECT user_id FROM users WHERE g IS NOT NULL ORDER BY random() LIMIT 1),
       NULL,
       'Generated comment ' || g || ' on post ' || p.post_id ||
       '. ' || (ARRAY['Useful breakdown, thanks.',
                      'Does this hold under concurrent writes?',
                      'I measured something similar on a larger dataset.',
                      'The trade-off section is the strongest part.',
                      'Would like to see the query plan for this.'])[1 + (g % 5)],
       CASE WHEN g % 50 = 0 THEN 'FLAGGED'
            WHEN g % 97 = 0 THEN 'HIDDEN'
            ELSE 'VISIBLE' END,
       p.created_at + (random() * INTERVAL '60 days')
FROM generate_series(1, 4000) AS g
CROSS JOIN LATERAL (
    SELECT post_id, created_at FROM posts WHERE g IS NOT NULL
    ORDER BY random() LIMIT 1
) p;

-- ~1 000 replies, attached to a parent on the same post (FK integrity by construction).
INSERT INTO comments (post_id, author_id, parent_comment_id, body, created_at)
SELECT parent.post_id,
       (SELECT user_id FROM users WHERE g IS NOT NULL ORDER BY random() LIMIT 1),
       parent.comment_id,
       'Generated reply ' || g || '. ' ||
       (ARRAY['Agreed.',
              'That matches what I saw.',
              'Only if the index is actually selective, though.',
              'Source for that number?',
              'This is the detail most posts leave out.'])[1 + (g % 5)],
       parent.created_at + (random() * INTERVAL '10 days')
FROM generate_series(1, 1000) AS g
CROSS JOIN LATERAL (
    SELECT comment_id, post_id, created_at
    FROM comments
    WHERE parent_comment_id IS NULL
      AND g IS NOT NULL
    ORDER BY random()
    LIMIT 1
) parent;

-- ~2 000 reviews (2 000 candidates less duplicate pairs). DISTINCT ON enforces the one-per-user-per-post rule
-- before the UNIQUE constraint has to reject anything.
INSERT INTO reviews (post_id, reviewer_id, rating, headline, body)
SELECT DISTINCT ON (post_id, reviewer_id)
       post_id,
       reviewer_id,
       rating,
       'Generated review: ' || (ARRAY['excellent','good','fair','mixed','weak'])[6 - rating],
       CASE WHEN rating >= 4 THEN 'Clear and well evidenced.' ELSE NULL END
FROM (
    SELECT (SELECT post_id FROM posts WHERE g IS NOT NULL
            ORDER BY random() LIMIT 1)                           AS post_id,
           (SELECT user_id FROM users WHERE g IS NOT NULL
            ORDER BY random() LIMIT 1)                           AS reviewer_id,
           1 + floor(random() * 5)::int                          AS rating
    FROM generate_series(1, 2000) AS g
) candidates
WHERE NOT EXISTS (
    SELECT 1 FROM reviews r
    WHERE r.post_id = candidates.post_id
      AND r.reviewer_id = candidates.reviewer_id
);

COMMIT;

-- Give the planner accurate statistics before anyone runs EXPLAIN.
ANALYZE;

-- ---------------------------------------------------------------------
-- Verification
-- ---------------------------------------------------------------------
SELECT 'users'     AS table_name, COUNT(*) AS rows FROM users
UNION ALL SELECT 'posts',     COUNT(*) FROM posts
UNION ALL SELECT 'tags',      COUNT(*) FROM tags
UNION ALL SELECT 'post_tags', COUNT(*) FROM post_tags
UNION ALL SELECT 'comments',  COUNT(*) FROM comments
UNION ALL SELECT 'reviews',   COUNT(*) FROM reviews
ORDER BY table_name;
