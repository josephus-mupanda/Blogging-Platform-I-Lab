-- =====================================================================
-- Smart Blogging Platform - Benchmark volume (PostgreSQL)
-- Phase 4
--
-- Builds a dataset large enough that an index decision is visible in the
-- timings. The demo seed (508 posts) is index-immune: every table fits in a
-- few pages and the planner rightly seq-scans it whatever indexes exist.
--
-- Run against a SEPARATE, empty database, never the demo one:
--   psql -U postgres -p 5433 -c "CREATE DATABASE blogging_platform_bench"
--   psql -U postgres -p 5433 -d blogging_platform_bench -f db/postgres/V1__schema.sql
--   psql -U postgres -p 5433 -d blogging_platform_bench -f db/bench/scale.sql
--
-- V2__indexes.sql is deliberately NOT run here: the benchmark suite drops and
-- creates those indexes itself, so it can time every query both ways.
--
-- Volume (approximate): 5 000 users, 100 000 posts, 200 tags, 250 000 tag
-- links, 630 000 comments in threads three levels deep, 250 000 reviews.
-- Takes one to two minutes.
--
-- Text is drawn from vocabularies rather than repeated, so a search has a
-- realistic selectivity: common words match a large share of rows, and the
-- synthetic rare terms (term0000-term1999 in posts, kw0000-kw4999 in
-- comments) match around a hundred each. A benchmark that only ever searches
-- for a word in every row measures nothing useful about an index.
--
-- Random picks reference the generating row (g, p.post_id) so PostgreSQL
-- re-evaluates them per row; see the note in V3__seed.sql.
-- =====================================================================

BEGIN;

SELECT setseed(0.20261102);

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, display_name, role)
SELECT 'bench_' || g,
       'bench_' || g || '@bench.blogplatform.io',
       '$2a$10$benchplaceholderhash' || lpad(g::text, 8, '0'),
       'Bench User ' || g,
       CASE WHEN g = 1 THEN 'ADMIN' WHEN g % 5 = 0 THEN 'READER' ELSE 'AUTHOR' END
FROM generate_series(1, 5000) AS g;

-- ---------------------------------------------------------------------
-- tags
-- ---------------------------------------------------------------------
INSERT INTO tags (name, slug)
SELECT 'topic-' || lpad(g::text, 3, '0'), 'topic-' || lpad(g::text, 3, '0')
FROM generate_series(1, 200) AS g;

-- ---------------------------------------------------------------------
-- posts: 100 000 over four years, 80% published
-- ---------------------------------------------------------------------
CREATE TEMP TABLE vocab_title_verb  (w text[]);
CREATE TEMP TABLE vocab_title_topic (w text[]);
CREATE TEMP TABLE vocab_body        (w text[]);

INSERT INTO vocab_title_verb VALUES (ARRAY[
  'Understanding','Debugging','Scaling','Refactoring','Benchmarking','Rethinking',
  'Profiling','Simplifying','Designing','Testing','Tuning','Migrating','Securing',
  'Observing','Partitioning','Replicating']);

INSERT INTO vocab_title_topic VALUES (ARRAY[
  'Connection Pools','Recursive Queries','Hash Indexes','Query Plans','Cache Eviction',
  'Batch Inserts','Transaction Isolation','Merge Sort','Foreign Keys','Document Schemas',
  'Trigram Search','Keyset Pagination','Write Amplification','Lock Contention',
  'Materialized Paths','Vacuum Tuning','Covering Indexes','Prepared Statements',
  'Event Sourcing','Read Replicas','Sharding Keys','Bloom Filters','Skip Lists',
  'B-Tree Splits','Garbage Collection','Thread Pools','Backpressure','Idempotent Writes',
  'Schema Migrations','Full-Text Ranking','Consistent Hashing','Rate Limiting',
  'Circuit Breakers','Message Queues','Time-Series Storage','Columnar Formats',
  'Query Caching','Slow Query Logs','Deadlock Detection','Optimistic Locking']);

INSERT INTO vocab_body VALUES (ARRAY[
  'index','query','planner','scan','sequential','bitmap','heap','buffer','cache','latency',
  'throughput','transaction','commit','rollback','isolation','snapshot','lock','row',
  'column','table','schema','constraint','foreign','primary','unique','partial',
  'expression','composite','selectivity','cardinality','statistics','histogram','vacuum',
  'autovacuum','bloat','page','tuple','visibility','checkpoint','wal','replica','primary',
  'failover','connection','pool','timeout','retry','backoff','batch','insert','update',
  'delete','upsert','merge','join','nested','loop','hash','sort','aggregate','window',
  'partition','shard','key','range','list','tree','node','leaf','split','fanout','depth',
  'trigram','token','stem','dictionary','rank','weight','document','collection','field',
  'array','embedded','reference','denormalized','normalized','redundancy','anomaly',
  'benchmark','median','percentile','warmup','iteration','variance','outlier','profile',
  'memory','allocation','garbage','pause','thread','executor','queue','producer',
  'consumer','stream','event','log','metric','trace','alert','dashboard','report',
  'measure','improve','regress','compare','baseline','optimize','refactor','simplify',
  'design','model','entity','relationship','attribute','cascade','orphan','integrity',
  'consistency','durability','atomicity','availability','partition','tolerance',
  'eventual','strong','quorum','leader','follower','election','heartbeat','lease']);

INSERT INTO posts (author_id, title, slug, body, excerpt, status, view_count,
                   published_at, created_at)
SELECT author_id,
       title,
       'bench-post-' || g,
       body,
       left(body, 160),
       status,
       floor(random() * 20000)::int,
       CASE WHEN status = 'PUBLISHED' THEN created_at + (random() * INTERVAL '3 days') END,
       created_at
FROM (
    SELECT g,
           2 + floor(random() * 4999)::bigint AS author_id,
           (SELECT w[1 + floor(random() * array_length(w, 1))::int] FROM vocab_title_verb
             WHERE g IS NOT NULL)
             || ' ' ||
           (SELECT w[1 + floor(random() * array_length(w, 1))::int] FROM vocab_title_topic
             WHERE g IS NOT NULL)
             || ', Part ' || (1 + g % 50)                          AS title,
           (SELECT string_agg(v.w[1 + floor(random() * array_length(v.w, 1))::int], ' ')
              FROM vocab_body v, generate_series(1, 60 + g % 60) AS n
             WHERE g IS NOT NULL)
             || ' term' || lpad(floor(random() * 2000)::text, 4, '0')
             || ' term' || lpad(floor(random() * 2000)::text, 4, '0')
             || '.'                                                 AS body,
           CASE WHEN g % 10 < 8 THEN 'PUBLISHED'
                WHEN g % 10 = 8 THEN 'DRAFT'
                ELSE 'ARCHIVED' END                                 AS status,
           now() - (random() * INTERVAL '1460 days')                AS created_at
    FROM generate_series(1, 100000) AS g
) generated;

-- 1-4 tags per post.
INSERT INTO post_tags (post_id, tag_id)
SELECT DISTINCT p.post_id, 1 + floor(random() * 200)::int
FROM posts p
CROSS JOIN LATERAL generate_series(1, 1 + (p.post_id % 4)::int) AS k;

-- ---------------------------------------------------------------------
-- comments: 500 000 roots, 100 000 replies, 30 000 replies to replies
-- ---------------------------------------------------------------------
INSERT INTO comments (post_id, author_id, parent_comment_id, body, status, created_at)
SELECT 1 + floor(random() * 100000)::bigint,
       2 + floor(random() * 4999)::bigint,
       NULL,
       'Comment ' || g || '. '
         || (ARRAY['Useful breakdown, thanks.',
                   'Does this hold under concurrent writes?',
                   'I measured something similar on a larger dataset.',
                   'The trade-off section is the strongest part.',
                   'Would like to see the query plan for this.',
                   'This matches what we saw in production.',
                   'What happens when the cache is cold?',
                   'Clear writing on a topic that is usually muddled.'])[1 + floor(random() * 8)::int]
         || ' kw' || lpad(floor(random() * 5000)::text, 4, '0'),
       CASE WHEN g % 50 = 0 THEN 'FLAGGED' WHEN g % 97 = 0 THEN 'HIDDEN' ELSE 'VISIBLE' END,
       now() - (random() * INTERVAL '1400 days')
FROM generate_series(1, 500000) AS g;

-- Replies are attached to a real parent on the same post, by joining on a
-- randomly chosen id rather than filtering with random() per scanned row.
INSERT INTO comments (post_id, author_id, parent_comment_id, body, created_at)
SELECT parent.post_id,
       2 + floor(random() * 4999)::bigint,
       parent.comment_id,
       'Reply ' || picks.g || '. '
         || (ARRAY['Agreed.', 'That matches what I saw.',
                   'Only if the index is actually selective, though.',
                   'Source for that number?',
                   'This is the detail most posts leave out.'])[1 + floor(random() * 5)::int]
         || ' kw' || lpad(floor(random() * 5000)::text, 4, '0'),
       parent.created_at + (random() * INTERVAL '10 days')
FROM (SELECT g, 1 + floor(random() * 500000)::bigint AS pick
        FROM generate_series(1, 100000) AS g) picks
JOIN comments parent ON parent.comment_id = picks.pick;

INSERT INTO comments (post_id, author_id, parent_comment_id, body, created_at)
SELECT parent.post_id,
       2 + floor(random() * 4999)::bigint,
       parent.comment_id,
       'Second-level reply ' || picks.g || '. Following up on the point above.'
         || ' kw' || lpad(floor(random() * 5000)::text, 4, '0'),
       parent.created_at + (random() * INTERVAL '5 days')
FROM (SELECT g, 500001 + floor(random() * 100000)::bigint AS pick
        FROM generate_series(1, 30000) AS g) picks
JOIN comments parent ON parent.comment_id = picks.pick;

-- ---------------------------------------------------------------------
-- reviews: 260 000 candidates, one per (post, reviewer)
-- ---------------------------------------------------------------------
INSERT INTO reviews (post_id, reviewer_id, rating, headline, body)
SELECT 1 + floor(random() * 100000)::bigint,
       2 + floor(random() * 4999)::bigint,
       rating,
       'Bench review: ' || (ARRAY['excellent','good','fair','mixed','weak'])[6 - rating],
       CASE WHEN rating >= 4 THEN 'Clear and well evidenced.' END
FROM (SELECT g, 1 + floor(random() * 5)::int AS rating
        FROM generate_series(1, 260000) AS g) candidates
ON CONFLICT ON CONSTRAINT uq_reviews_post_reviewer DO NOTHING;

COMMIT;

ANALYZE;

SELECT 'users' AS table_name, COUNT(*) AS rows FROM users
UNION ALL SELECT 'posts',     COUNT(*) FROM posts
UNION ALL SELECT 'tags',      COUNT(*) FROM tags
UNION ALL SELECT 'post_tags', COUNT(*) FROM post_tags
UNION ALL SELECT 'comments',  COUNT(*) FROM comments
UNION ALL SELECT 'reviews',   COUNT(*) FROM reviews
ORDER BY table_name;
