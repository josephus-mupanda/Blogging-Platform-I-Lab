package org.example.bloggingplatformi.model;

/** A user's role. These match the values allowed by the database check. */
public enum Role {
    ADMIN,
    AUTHOR,
    READER;

    /**
     * Reads a role from the database. An unknown value means bad data,
     * so it throws instead of picking a default.
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
