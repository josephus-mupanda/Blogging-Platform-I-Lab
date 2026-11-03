package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * A review: a rating from 1 to 5, with an optional headline and text.
 * The id is a String so it works for both PostgreSQL and MongoDB.
 * The criteria map holds optional sub-scores. Only MongoDB stores it.
 */
public record Review(
        String reviewId,
        long postId,
        long reviewerId,
        String reviewerName,
        int rating,
        String headline,
        String body,
        Map<String, Integer> criteria,
        int helpfulVotes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    public Review {
        // Same rule as the rating check in both databases.
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                    "rating must be between " + MIN_RATING + " and " + MAX_RATING + ", was " + rating);
        }
        if (helpfulVotes < 0) {
            throw new IllegalArgumentException("helpfulVotes must not be negative");
        }
        criteria = (criteria == null) ? Map.of() : Map.copyOf(criteria);
    }

    /** A new review, not yet saved. */
    public static Review of(long postId, long reviewerId, String reviewerName,
                            int rating, String headline, String body) {
        return new Review(null, postId, reviewerId, reviewerName, rating, headline, body,
                Map.of(), 0, null, null);
    }

    public boolean isPersisted() {
        return reviewId != null && !reviewId.isBlank();
    }

    public Review withId(String id) {
        return new Review(id, postId, reviewerId, reviewerName, rating, headline, body,
                criteria, helpfulVotes, createdAt, updatedAt);
    }

    public Review withCriteria(Map<String, Integer> newCriteria) {
        return new Review(reviewId, postId, reviewerId, reviewerName, rating, headline, body,
                newCriteria, helpfulVotes, createdAt, updatedAt);
    }

    public Review withRating(int newRating, String newHeadline, String newBody) {
        return new Review(reviewId, postId, reviewerId, reviewerName, newRating, newHeadline,
                newBody, criteria, helpfulVotes, createdAt, updatedAt);
    }

    /** The rating as stars, for example 3 becomes ★★★☆☆. */
    public String stars() {
        return "★".repeat(rating) + "☆".repeat(MAX_RATING - rating);
    }

    /** Throws if the reviewer name is missing, because the name is stored on the review. */
    public static Review requireNamed(Review review) {
        Objects.requireNonNull(review.reviewerName, "reviewerName");
        return review;
    }
}
