package org.example.bloggingplatformi.model;

/**
 * The status of a comment. PostgreSQL and MongoDB both accept only these three values.
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
}
