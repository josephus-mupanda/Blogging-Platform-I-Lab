package org.example.bloggingplatformi.model;

/** Mirrors the {@code ck_users_role} CHECK constraint. */
public enum Role {
    ADMIN,
    AUTHOR,
    READER;

    /**
     * Parses a database value. Unknown input is a data-integrity problem
     * rather than a user mistake, so it fails loudly instead of defaulting.
     */
    public static Role from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        return Role.valueOf(value.trim().toUpperCase());
    }

    public boolean canPublish() {
        return this == ADMIN || this == AUTHOR;
    }

    public boolean canModerate() {
        return this == ADMIN;
    }
}
