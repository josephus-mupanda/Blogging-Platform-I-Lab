-- =====================================================================
-- Smart Blogging Platform: indexes (PostgreSQL).
--
-- Run as:  psql -U postgres -d blogging_platform -f db/postgres/V2__indexes.sql
--
-- Kept apart from V1__schema.sql so the same queries can be timed before
-- and after indexing. To go back to no indexes, run the DROP block at the
-- bottom of this file.
--
-- Primary keys and UNIQUE constraints already have their own indexes,
-- so they are not repeated here.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- posts
-- ---------------------------------------------------------------------

-- Speeds up "posts by author". PostgreSQL does not index foreign keys by
-- itself, so deleting a user would also scan the whole posts table.
CREATE INDEX IF NOT EXISTS idx_posts_author_id
    ON posts (author_id);

-- Speeds up the main feed: published posts, newest first.
-- The filter column comes first and the sort column second, so no extra sort is needed.
CREATE INDEX IF NOT EXISTS idx_posts_status_published
    ON posts (status, published_at DESC);

-- Speeds up case-insensitive title lookup: WHERE LOWER(title) = LOWER(?).
-- A plain index on title cannot be used for LOWER(title).
CREATE INDEX IF NOT EXISTS idx_posts_title_lower
    ON posts (LOWER(title));

-- Speeds up title search with ILIKE '%term%'. A normal B-tree index
-- cannot help when the pattern starts with %, but a trigram index can.
CREATE INDEX IF NOT EXISTS idx_posts_title_trgm
    ON posts USING GIN (title gin_trgm_ops);

-- Full-text search over title and body. Words in the title get a
-- higher weight than words in the body.
CREATE INDEX IF NOT EXISTS idx_posts_search_tsv
    ON posts USING GIN ((
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(body,  '')), 'B')
    ));

-- Speeds up "newest posts first" and keyset pagination.
CREATE INDEX IF NOT EXISTS idx_posts_created_at
    ON posts (created_at DESC);

-- Speeds up "most viewed posts". Only published posts are indexed,
-- because drafts never appear in that list.
CREATE INDEX IF NOT EXISTS idx_posts_views_published
    ON posts (view_count DESC)
    WHERE status = 'PUBLISHED';

-- ---------------------------------------------------------------------
-- tags and post_tags
-- ---------------------------------------------------------------------

-- Speeds up case-insensitive tag lookup by name.
CREATE INDEX IF NOT EXISTS idx_tags_name_lower
    ON tags (LOWER(name));

-- Speeds up tag autocomplete: WHERE name ILIKE 'jav%'.
CREATE INDEX IF NOT EXISTS idx_tags_name_trgm
    ON tags USING GIN (name gin_trgm_ops);

-- Speeds up "all posts with this tag". The primary key (post_id, tag_id)
-- only helps when we start from the post, so we need the reverse order.
CREATE INDEX IF NOT EXISTS idx_post_tags_tag_id
    ON post_tags (tag_id, post_id);

-- ---------------------------------------------------------------------
-- comments
-- ---------------------------------------------------------------------

-- Speeds up loading one post's comments in date order.
CREATE INDEX IF NOT EXISTS idx_comments_post_created
    ON comments (post_id, created_at);

-- Speeds up finding the replies to a comment. Top-level comments have a
-- NULL parent, so they are left out to keep the index small.
CREATE INDEX IF NOT EXISTS idx_comments_parent
    ON comments (parent_comment_id)
    WHERE parent_comment_id IS NOT NULL;

-- Speeds up "comments by user" and the SET NULL when a user is deleted.
CREATE INDEX IF NOT EXISTS idx_comments_author
    ON comments (author_id);

-- Speeds up the moderation queue: hidden or flagged comments, newest first.
-- Only non-visible comments (about 2%) are indexed. The key is created_at
-- alone, so PostgreSQL can read the newest 50 and stop without sorting.
CREATE INDEX IF NOT EXISTS idx_comments_moderation
    ON comments (created_at DESC)
    WHERE status <> 'VISIBLE';

-- Speeds up keyword search in comments: body ILIKE '%term%'.
-- Without it, this search took about 1.6 s over 630 000 comments.
CREATE INDEX IF NOT EXISTS idx_comments_body_trgm
    ON comments USING GIN (body gin_trgm_ops);

-- ---------------------------------------------------------------------
-- reviews
-- ---------------------------------------------------------------------

-- Speeds up the average rating per post. Both columns are in the index,
-- so AVG and COUNT can be read from the index without the table.
CREATE INDEX IF NOT EXISTS idx_reviews_post_rating
    ON reviews (post_id, rating);

-- Speeds up "reviews by user", newest first.
CREATE INDEX IF NOT EXISTS idx_reviews_reviewer
    ON reviews (reviewer_id, created_at DESC);

COMMIT;

-- Update table statistics so the query planner knows about the new indexes.
ANALYZE users;
ANALYZE posts;
ANALYZE tags;
ANALYZE post_tags;
ANALYZE comments;
ANALYZE reviews;

-- =====================================================================
-- Remove all indexes above, for benchmarking without indexes.
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
-- DROP INDEX IF EXISTS idx_comments_body_trgm;
-- DROP INDEX IF EXISTS idx_reviews_post_rating;
-- DROP INDEX IF EXISTS idx_reviews_reviewer;
