package org.example.bloggingplatformi.model;

import java.time.LocalDateTime;

/** A row of the users table. A record is a class that only holds data. */
public record User(
        long id,
        String username,
        String email,
        String passwordHash,
        String displayName,
        Role role,
        boolean active,
        LocalDateTime createdAt) {

    /** What a ComboBox shows for this user (and it keeps the password hash out of the logs). */
    @Override
    public String toString() {
        return displayName;
    }
}
