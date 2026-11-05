-- =====================================================================
-- Smart Blogging Platform: PostgreSQL tables.
-- Run: psql -U postgres -p 5433 -d blogging_platform -f db/postgres/schema.sql
--
-- Users, posts and tags are here. Comments and reviews are in MongoDB
-- (see db/mongo/setup.js).
-- Warning: this deletes the tables first, so all their data is lost.
-- =====================================================================

DROP TABLE IF EXISTS post_tags;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS posts;
DROP TABLE IF EXISTS users;

-- One row per account. The role decides what the user may do.
CREATE TABLE users (
    user_id       BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(64)  NOT NULL,          -- SHA-256 of the password, never the password itself
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN', 'AUTHOR', 'READER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- One row per blog post. Each post has one author (many posts to one user).
CREATE TABLE posts (
    post_id    BIGSERIAL    PRIMARY KEY,
    author_id  BIGINT       NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL CHECK (trim(title) <> ''),
    body       TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_posts_normalized_title ON posts (lower(btrim(title)));
CREATE INDEX idx_posts_archived ON posts (deleted_at DESC) WHERE deleted_at IS NOT NULL;

-- One row per tag name, for example "java".
CREATE TABLE tags (
    tag_id BIGSERIAL   PRIMARY KEY,
    name   VARCHAR(50) NOT NULL UNIQUE,
    deleted_at TIMESTAMPTZ
);

-- Many-to-many link: a post has many tags, and a tag is on many posts.
-- The primary key is the pair, so the same tag cannot be added twice to a post.
CREATE TABLE post_tags (
    post_id BIGINT NOT NULL REFERENCES posts (post_id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tags (tag_id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);
