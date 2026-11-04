package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A row of the users table. The id is 0 until the user is saved.
 * toString() leaves out the password hash so it never ends up in a log.
 */
public record User(
        long userId,
        String username,
        String email,
        String passwordHash,
        String displayName,
        String bio,
        Role role,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public User {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(role, "role");
    }

    /** A new user, not yet saved. */
    public static User newUser(String username, String email, String passwordHash,
                               String displayName, String bio, Role role) {
        return new User(0L, username, email, passwordHash, displayName, bio, role, true, null, null);
    }

    public boolean isPersisted() {
        return userId > 0;
    }

    public User withId(long id) {
        return new User(id, username, email, passwordHash, displayName, bio, role,
                active, createdAt, updatedAt);
    }

    public User withActive(boolean newActive) {
        return new User(userId, username, email, passwordHash, displayName, bio, role,
                newActive, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        // The password hash is left out.
        return "User[userId=" + userId
                + ", username=" + username
                + ", email=" + email
                + ", displayName=" + displayName
                + ", role=" + role
                + ", active=" + active + "]";
    }
}
