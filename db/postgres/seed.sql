-- =====================================================================
-- Smart Blogging Platform: starting data.
-- Run after schema.sql:
--   psql -U postgres -p 5433 -d blogging_platform -f db/postgres/seed.sql
--
-- Only the admin account. Bloggers and readers sign up in the app.
-- Username: admin   Password: 111111
-- The password column holds the SHA-256 of "111111", not the password itself.
-- =====================================================================

INSERT INTO users (username, email, password_hash, display_name, role)
VALUES ('admin', 'admin@blogplatform.io',
        'bcb15f821479b4d5772bd0ca866c00ad5f926e3580720659cc80d39c9d09802a',
        'Site Admin', 'ADMIN');
