package org.example.bloggingplatformi.model;

/** The status of a post. These match the values allowed by the database check. */
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

    /** Only PUBLISHED posts have a published date. The database checks this too. */
    public boolean requiresPublishedAt() {
        return this == PUBLISHED;
    }

    /** Whether readers should see the post in listings. */
    public boolean isVisibleToReaders() {
        return this == PUBLISHED;
    }
}
