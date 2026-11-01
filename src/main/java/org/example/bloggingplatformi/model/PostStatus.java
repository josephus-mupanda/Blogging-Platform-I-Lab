package org.example.bloggingplatformi.model;

/** Mirrors the {@code ck_posts_status} CHECK constraint. */
public enum PostStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public static PostStatus from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return PostStatus.valueOf(value.trim().toUpperCase());
    }

    /**
     * Only PUBLISHED posts carry a publication date; the
     * {@code ck_posts_published_at} constraint enforces it in the database.
     */
    public boolean requiresPublishedAt() {
        return this == PUBLISHED;
    }

    /** Whether readers should see the post in listings. */
    public boolean isVisibleToReaders() {
        return this == PUBLISHED;
    }
}
