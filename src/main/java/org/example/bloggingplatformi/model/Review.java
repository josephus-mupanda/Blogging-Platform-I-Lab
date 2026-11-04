package org.example.bloggingplatformi.model;

import java.time.LocalDateTime;

/** A review, stored in MongoDB: a rating from 1 to 5 and an optional text. */
public record Review(
        String id,
        long postId,
        long reviewerId,
        String reviewerName,
        int rating,
        String text,
        LocalDateTime createdAt) {

    /** What the review list shows, for example "4/5 josephus: Very clear". */
    @Override
    public String toString() {
        return rating + "/5  " + reviewerName + (text == null || text.isEmpty() ? "" : ":  " + text);
    }
}
