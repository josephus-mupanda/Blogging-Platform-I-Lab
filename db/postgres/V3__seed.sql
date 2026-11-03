-- =====================================================================
-- Smart Blogging Platform: starting data (PostgreSQL).
--
-- Run as:  psql -U postgres -d blogging_platform -f db/postgres/V3__seed.sql
--
-- It empties every table and creates only the admin account.
-- Bloggers and readers sign up in the app; posts, tags, comments and
-- reviews are entered through the app as well.
--
-- The large dataset for the benchmarks is in db/bench/scale.sql and goes
-- into a separate database (blogging_platform_bench).
-- =====================================================================

BEGIN;

TRUNCATE reviews, comments, post_tags, tags, posts, users RESTART IDENTITY CASCADE;

-- The admin account. Username: admin, password: 111111.
-- The password is stored as a PBKDF2 hash (salt:hash), made by util/Passwords.java.
INSERT INTO users (username, email, password_hash, display_name, bio, role) VALUES
    ('admin', 'admin@blogplatform.io',
     'mM0USplpGPPxEI8CRRqHfA==:dGX/z9hLD2voC1cEfxu8k8+mDoa9NO58xxyQZ/W/gvM=',
     'Site Admin', NULL, 'ADMIN');

COMMIT;

-- Check: row count for each table.
SELECT 'users'     AS table_name, COUNT(*) AS rows FROM users
UNION ALL SELECT 'posts',     COUNT(*) FROM posts
UNION ALL SELECT 'tags',      COUNT(*) FROM tags
UNION ALL SELECT 'post_tags', COUNT(*) FROM post_tags
UNION ALL SELECT 'comments',  COUNT(*) FROM comments
UNION ALL SELECT 'reviews',   COUNT(*) FROM reviews
ORDER BY table_name;
