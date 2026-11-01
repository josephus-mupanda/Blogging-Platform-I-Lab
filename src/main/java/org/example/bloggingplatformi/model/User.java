package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A row of {@code users}.
 *
 * <p>{@code userId} is {@code 0} for an instance that has not been inserted
 * yet; the database assigns the real value from the sequence.
 *
 * <p>{@code passwordHash} is carried because the DAO has to write it, but it
 * is excluded from {@link #toString()} so it cannot leak into a log line.
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

    /** A new, not-yet-persisted user. */
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

    public User withProfile(String newDisplayName, String newBio) {
        return new User(userId, username, email, passwordHash, newDisplayName, newBio, role,
                active, createdAt, updatedAt);
    }

    public User withActive(boolean newActive) {
        return new User(userId, username, email, passwordHash, displayName, bio, role,
                newActive, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        // passwordHash deliberately omitted.
        return "User[userId=" + userId
                + ", username=" + username
                + ", email=" + email
                + ", displayName=" + displayName
                + ", role=" + role
                + ", active=" + active + "]";
    }
}
