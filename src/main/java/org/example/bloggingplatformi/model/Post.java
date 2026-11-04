package org.example.bloggingplatformi.model;

import java.time.LocalDateTime;

/** A row of the posts table, plus the author's name (read with a JOIN on users). */
public record Post(
        long id,
        long authorId,
        String authorName,
        String title,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
