package org.example.bloggingplatformi.model;

import java.time.LocalDateTime;

import org.example.bloggingplatformi.ui.Dates;

/**
 * A comment, stored in MongoDB.
 * The id is a MongoDB ObjectId written as text; postId and authorId are PostgreSQL ids.
 */
public record Comment(
        String id,
        long postId,
        long authorId,
        String authorName,
        String body,
        LocalDateTime createdAt) {

    /** What the comment list shows. */
    @Override
    public String toString() {
        return authorName + " (" + Dates.format(createdAt) + "):  " + body;
    }
}
