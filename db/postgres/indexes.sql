-- =====================================================================
-- Smart Blogging Platform: indexes.
-- Run after schema.sql:
--   psql -U postgres -p 5433 -d blogging_platform -f db/postgres/indexes.sql
--
-- They are in their own file so the benchmark can measure the queries
-- without them first, and then with them.
-- PostgreSQL already indexes every PRIMARY KEY and UNIQUE column, so
-- users.username, users.email and tags.name (the tag name) are indexed already.
-- =====================================================================

-- Needed for the "trigram" indexes below.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Posts by an author: WHERE author_id = ?
-- PostgreSQL does not index foreign keys by itself.
CREATE INDEX IF NOT EXISTS idx_posts_author_id ON posts (author_id);

-- Keyword search: WHERE title ILIKE '%word%' OR body ILIKE '%word%'
-- A normal index cannot help with '%word%' (the word can be anywhere in the text).
-- A trigram index can: it indexes every group of 3 letters.
CREATE INDEX IF NOT EXISTS idx_posts_title_trgm ON posts USING GIN (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_posts_body_trgm  ON posts USING GIN (body gin_trgm_ops);

-- Posts with a tag: WHERE tag_id = ?
-- The primary key (post_id, tag_id) starts with post_id, so it cannot help here.
CREATE INDEX IF NOT EXISTS idx_post_tags_tag_id ON post_tags (tag_id);

-- The post list is sorted newest first: ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts (created_at DESC);
