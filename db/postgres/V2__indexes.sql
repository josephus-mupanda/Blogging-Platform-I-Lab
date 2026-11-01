-- =====================================================================
-- Smart Blogging Platform - Index Strategy (PostgreSQL)
-- Phase 1 / Epic 1 - User Story 1.2
--
-- Run as:  psql -U postgres -d blogging_platform -f db/postgres/V2__indexes.sql
--
-- This file is separate from V1__schema.sql on purpose. Phase 4 measures
-- query plans BEFORE running this script and AFTER, so the performance
-- report compares the same queries on the same data with and without
-- indexing. To reset to the unindexed baseline, run the DROP block at the
-- bottom of this file.
--
-- Primary keys and UNIQUE constraints already create implicit B-tree
-- indexes; those are not repeated here.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- posts
-- ---------------------------------------------------------------------

-- Foreign-key column. PostgreSQL does NOT index FK columns automatically,
-- and an unindexed FK makes both "posts by author" and the cascade on
-- user deletion scan the whole table.
CREATE INDEX IF NOT EXISTS idx_posts_author_id
    ON posts (author_id);

-- The default feed: WHERE status = 'PUBLISHED' ORDER BY published_at DESC.
-- Equality column first, sort column second, so one index serves the
-- filter and removes the sort node from the plan entirely.
CREATE INDEX IF NOT EXISTS idx_posts_status_published
    ON posts (status, published_at DESC);

-- Case-insensitive title lookup: WHERE LOWER(title) = LOWER(?).
-- An expression index is required; a plain index on title cannot serve it.
CREATE INDEX IF NOT EXISTS idx_posts_title_lower
    ON posts (LOWER(title));

-- Substring search: WHERE title ILIKE '%term%'. A B-tree is useless for a
-- leading wildcard; a trigram GIN index is not.
CREATE INDEX IF NOT EXISTS idx_posts_title_trgm
    ON posts USING GIN (title gin_trgm_ops);

-- Full-text keyword search across title and body, with the title weighted
-- above the body so a term in the headline ranks higher.
CREATE INDEX IF NOT EXISTS idx_posts_search_tsv
    ON posts USING GIN ((
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(body,  '')), 'B')
    ));

-- Newest-first listing and keyset pagination.
CREATE INDEX IF NOT EXISTS idx_posts_created_at
    ON posts (created_at DESC);

-- Trending / most-viewed, restricted to published rows. Partial, because
-- drafts are never shown in that list and do not belong in the index.
CREATE INDEX IF NOT EXISTS idx_posts_views_published
    ON posts (view_count DESC)
    WHERE status = 'PUBLISHED';

-- ---------------------------------------------------------------------
-- tags and post_tags
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_tags_name_lower
    ON tags (LOWER(name));

-- Tag autocomplete: WHERE name ILIKE 'jav%'.
CREATE INDEX IF NOT EXISTS idx_tags_name_trgm
    ON tags USING GIN (name gin_trgm_ops);

-- The composite PK (post_id, tag_id) only supports post-first access.
-- "All posts carrying this tag" needs the reverse order.
CREATE INDEX IF NOT EXISTS idx_post_tags_tag_id
    ON post_tags (tag_id, post_id);

-- ---------------------------------------------------------------------
-- comments
-- ---------------------------------------------------------------------

-- Load one post's thread in chronological order: filter and sort together.
CREATE INDEX IF NOT EXISTS idx_comments_post_created
    ON comments (post_id, created_at);

-- Reply resolution. Partial, because most comments are top level and NULL
-- parents would otherwise dominate the index for no benefit.
CREATE INDEX IF NOT EXISTS idx_comments_parent
    ON comments (parent_comment_id)
    WHERE parent_comment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_comments_author
    ON comments (author_id);

-- Moderation queue: the non-visible minority.
CREATE INDEX IF NOT EXISTS idx_comments_moderation
    ON comments (status, created_at DESC)
    WHERE status <> 'VISIBLE';

-- ---------------------------------------------------------------------
-- reviews
-- ---------------------------------------------------------------------

-- Rating aggregate per post. Covering (post_id, rating) lets AVG/COUNT be
-- answered index-only, without touching the heap.
CREATE INDEX IF NOT EXISTS idx_reviews_post_rating
    ON reviews (post_id, rating);

CREATE INDEX IF NOT EXISTS idx_reviews_reviewer
    ON reviews (reviewer_id, created_at DESC);

COMMIT;

-- Refresh planner statistics so the first EXPLAIN after indexing is fair.
ANALYZE users;
ANALYZE posts;
ANALYZE tags;
ANALYZE post_tags;
ANALYZE comments;
ANALYZE reviews;

-- =====================================================================
-- Baseline reset for Phase 4 benchmarking.
-- Uncomment and run to return the database to its unindexed state.
-- =====================================================================
-- DROP INDEX IF EXISTS idx_posts_author_id;
-- DROP INDEX IF EXISTS idx_posts_status_published;
-- DROP INDEX IF EXISTS idx_posts_title_lower;
-- DROP INDEX IF EXISTS idx_posts_title_trgm;
-- DROP INDEX IF EXISTS idx_posts_search_tsv;
-- DROP INDEX IF EXISTS idx_posts_created_at;
-- DROP INDEX IF EXISTS idx_posts_views_published;
-- DROP INDEX IF EXISTS idx_tags_name_lower;
-- DROP INDEX IF EXISTS idx_tags_name_trgm;
-- DROP INDEX IF EXISTS idx_post_tags_tag_id;
-- DROP INDEX IF EXISTS idx_comments_post_created;
-- DROP INDEX IF EXISTS idx_comments_parent;
-- DROP INDEX IF EXISTS idx_comments_author;
-- DROP INDEX IF EXISTS idx_comments_moderation;
-- DROP INDEX IF EXISTS idx_reviews_post_rating;
-- DROP INDEX IF EXISTS idx_reviews_reviewer;
