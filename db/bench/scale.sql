-- =====================================================================
-- Smart Blogging Platform: a big database, for the benchmark only.
-- With only a few rows PostgreSQL reads the whole table anyway, so an
-- index makes no difference. With 100 000 posts it does.
--
-- Run it on a SEPARATE database, not the real one:
--   psql -U postgres -p 5433 -c "CREATE DATABASE blogging_platform_bench"
--   psql -U postgres -p 5433 -d blogging_platform_bench -f db/postgres/schema.sql
--   psql -U postgres -p 5433 -d blogging_platform_bench -f db/bench/scale.sql
-- Do not run indexes.sql there: the benchmark adds the indexes itself.
-- =====================================================================

-- 1 000 authors.
INSERT INTO users (username, email, password_hash, display_name, role)
SELECT 'user' || g, 'user' || g || '@bench.io', 'not-a-real-hash', 'User ' || g, 'AUTHOR'
FROM generate_series(1, 1000) AS g;

-- 5 tags.
INSERT INTO tags (name) VALUES ('java'), ('sql'), ('mongodb'), ('javafx'), ('caching');

-- 100 000 posts, one every minute going back in time.
INSERT INTO posts (author_id, title, body, created_at)
SELECT 1 + (g % 1000),
       'Post number ' || g || ' about ' || (ARRAY['java', 'sql', 'mongodb', 'javafx', 'caching'])[1 + g % 5],
       'This is the text of post number ' || g || '.',
       now() - g * INTERVAL '1 minute'
FROM generate_series(1, 100000) AS g;

-- Each post gets one tag.
INSERT INTO post_tags (post_id, tag_id)
SELECT post_id, 1 + post_id % 5 FROM posts;

-- Update the statistics PostgreSQL uses to plan its queries.
ANALYZE;
