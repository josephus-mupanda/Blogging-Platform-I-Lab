-- Adds recoverable deletion to an existing database. Safe to rerun.
BEGIN;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_posts_archived ON posts (deleted_at DESC) WHERE deleted_at IS NOT NULL;
COMMIT;
