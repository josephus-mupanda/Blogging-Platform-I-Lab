-- =====================================================================
-- Smart Blogging Platform - Physical Schema (PostgreSQL 14+)
-- Phase 1 / Epic 1 - User Stories 1.1 and 1.2
--
-- Run as:  psql -U postgres -d blogging_platform -f db/postgres/V1__schema.sql
--
-- Indexes are intentionally NOT in this file. They live in V2__indexes.sql so
-- that Phase 4 can measure query performance with and without them.
-- =====================================================================

BEGIN;

-- Extensions -----------------------------------------------------------
-- pg_trgm powers the trigram GIN index used for ILIKE '%term%' search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Clean re-runnable drop, children first ------------------------------
DROP TABLE IF EXISTS reviews    CASCADE;
DROP TABLE IF EXISTS comments   CASCADE;
DROP TABLE IF EXISTS post_tags  CASCADE;
DROP TABLE IF EXISTS tags       CASCADE;
DROP TABLE IF EXISTS posts      CASCADE;
DROP TABLE IF EXISTS users      CASCADE;
DROP FUNCTION IF EXISTS set_updated_at() CASCADE;

-- ---------------------------------------------------------------------
-- Shared trigger function: keeps updated_at honest regardless of client
-- ---------------------------------------------------------------------
CREATE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id       BIGSERIAL,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    bio           TEXT,
    role          VARCHAR(20)  NOT NULL DEFAULT 'AUTHOR',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users          PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT ck_users_username CHECK (char_length(trim(username)) >= 3),
    CONSTRAINT ck_users_email    CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT ck_users_role     CHECK (role IN ('ADMIN', 'AUTHOR', 'READER'))
);

COMMENT ON TABLE  users      IS 'Platform accounts: admins, authors and readers.';
COMMENT ON COLUMN users.role IS 'ADMIN moderates, AUTHOR publishes, READER comments and reviews.';

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- posts
-- ---------------------------------------------------------------------
CREATE TABLE posts (
    post_id      BIGSERIAL,
    author_id    BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    slug         VARCHAR(280) NOT NULL,
    body         TEXT         NOT NULL,
    excerpt      VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    view_count   INTEGER      NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_posts        PRIMARY KEY (post_id),
    CONSTRAINT fk_posts_author FOREIGN KEY (author_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT uq_posts_slug   UNIQUE (slug),
    CONSTRAINT ck_posts_title  CHECK (char_length(trim(title)) > 0),
    CONSTRAINT ck_posts_body   CHECK (char_length(trim(body)) > 0),
    CONSTRAINT ck_posts_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_posts_views  CHECK (view_count >= 0),
    -- A published post must carry a publication date; a draft must not.
    CONSTRAINT ck_posts_published_at CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
     OR (status <> 'PUBLISHED')
    )
);

COMMENT ON COLUMN posts.slug       IS 'Immutable public identifier; survives title edits.';
COMMENT ON COLUMN posts.view_count IS 'Deliberate denormalization - see design doc section 4.';
COMMENT ON COLUMN posts.excerpt    IS 'Precomputed from body to keep list queries narrow.';

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- tags
-- ---------------------------------------------------------------------
CREATE TABLE tags (
    tag_id     SERIAL,
    name       VARCHAR(50) NOT NULL,
    slug       VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_tags       PRIMARY KEY (tag_id),
    CONSTRAINT uq_tags_name  UNIQUE (name),
    CONSTRAINT uq_tags_slug  UNIQUE (slug),
    -- Stored lower-case so that uniqueness is genuinely case-insensitive.
    CONSTRAINT ck_tags_lower CHECK (name = lower(name)),
    CONSTRAINT ck_tags_blank CHECK (char_length(trim(name)) > 0)
);

COMMENT ON TABLE tags IS 'Controlled vocabulary; names are normalized to lower case.';

-- ---------------------------------------------------------------------
-- post_tags (associative entity resolving the M:N relationship)
-- ---------------------------------------------------------------------
CREATE TABLE post_tags (
    post_id   BIGINT      NOT NULL,
    tag_id    INTEGER     NOT NULL,
    tagged_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_post_tags      PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id)
        REFERENCES posts (post_id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tags_tag  FOREIGN KEY (tag_id)
        REFERENCES tags (tag_id) ON DELETE CASCADE
);

COMMENT ON TABLE post_tags IS 'The composite PK also prevents the same tag twice on one post.';

-- ---------------------------------------------------------------------
-- comments (relational form; mirrored as documents in MongoDB)
-- ---------------------------------------------------------------------
CREATE TABLE comments (
    comment_id        BIGSERIAL,
    post_id           BIGINT      NOT NULL,
    author_id         BIGINT,
    parent_comment_id BIGINT,
    body              TEXT        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_comments         PRIMARY KEY (comment_id),
    CONSTRAINT fk_comments_post    FOREIGN KEY (post_id)
        REFERENCES posts (post_id) ON DELETE CASCADE,
    -- SET NULL, not CASCADE: deleting an account must not erase a thread
    -- other people replied to.
    CONSTRAINT fk_comments_author  FOREIGN KEY (author_id)
        REFERENCES users (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_comments_parent  FOREIGN KEY (parent_comment_id)
        REFERENCES comments (comment_id) ON DELETE CASCADE,
    CONSTRAINT ck_comments_body    CHECK (char_length(trim(body)) > 0),
    CONSTRAINT ck_comments_status  CHECK (status IN ('VISIBLE', 'HIDDEN', 'FLAGGED')),
    CONSTRAINT ck_comments_noself  CHECK (parent_comment_id IS DISTINCT FROM comment_id)
);

COMMENT ON COLUMN comments.author_id IS 'NULL means the account was deleted; render as [deleted user].';

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- reviews
-- ---------------------------------------------------------------------
CREATE TABLE reviews (
    review_id   BIGSERIAL,
    post_id     BIGINT       NOT NULL,
    reviewer_id BIGINT       NOT NULL,
    rating      SMALLINT     NOT NULL,
    headline    VARCHAR(150),
    body        TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_reviews          PRIMARY KEY (review_id),
    CONSTRAINT fk_reviews_post     FOREIGN KEY (post_id)
        REFERENCES posts (post_id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_reviews_rating   CHECK (rating BETWEEN 1 AND 5),
    -- One review per user per post.
    CONSTRAINT uq_reviews_post_reviewer UNIQUE (post_id, reviewer_id)
);

CREATE TRIGGER trg_reviews_updated_at
    BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- Convenience view: post list rows without dragging full bodies around.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_post_summary AS
SELECT p.post_id,
       p.title,
       p.slug,
       p.excerpt,
       p.status,
       p.view_count,
       p.published_at,
       p.created_at,
       u.user_id      AS author_id,
       u.username     AS author_username,
       u.display_name AS author_display_name,
       COALESCE(c.comment_count, 0) AS comment_count,
       COALESCE(r.review_count, 0)  AS review_count,
       r.avg_rating
FROM posts p
JOIN users u ON u.user_id = p.author_id
LEFT JOIN (
    SELECT post_id, COUNT(*) AS comment_count
    FROM comments
    WHERE status = 'VISIBLE'
    GROUP BY post_id
) c ON c.post_id = p.post_id
LEFT JOIN (
    SELECT post_id,
           COUNT(*)                        AS review_count,
           ROUND(AVG(rating)::numeric, 2)  AS avg_rating
    FROM reviews
    GROUP BY post_id
) r ON r.post_id = p.post_id;

COMMIT;
