package org.example.bloggingplatformi.model;

/** What a user is allowed to do. The same three values are allowed by the users table. */
public enum Role {
    ADMIN,   // everything, plus the Tags and Users screens
    AUTHOR,  // a blogger: writes posts
    READER;  // reads, comments and reviews

    public boolean canWritePosts() {
        return this == ADMIN || this == AUTHOR;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
