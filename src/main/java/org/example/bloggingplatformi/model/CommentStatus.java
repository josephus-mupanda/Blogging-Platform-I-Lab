package org.example.bloggingplatformi.model;

/**
 * Mirrors the {@code ck_comments_status} CHECK constraint and the
 * {@code status} enum in the MongoDB {@code $jsonSchema} validator. Both
 * stores accept exactly these three values.
 */
public enum CommentStatus {
    VISIBLE,
    HIDDEN,
    FLAGGED;

    public static CommentStatus from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return CommentStatus.valueOf(value.trim().toUpperCase());
    }

    /** FLAGGED and HIDDEN comments are the moderation queue. */
    public boolean needsModeration() {
        return this != VISIBLE;
    }
}
