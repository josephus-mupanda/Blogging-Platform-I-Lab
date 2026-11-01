package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * A review: a 1-5 rating plus optional prose.
 *
 * <p>Like {@link Comment}, the identifier is a {@code String} so the same
 * record serves the relational and document implementations.
 *
 * <p>{@code criteria} holds optional per-review sub-scores (clarity, depth,
 * accuracy, and so on). The set of keys varies from one review to the next,
 * which in SQL means a wide sparse table or an EAV side table, and in a
 * document is simply a field. It is the clearest argument in this domain for
 * the document model; the JDBC implementation returns it empty.
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
        // Mirrors CHECK (rating BETWEEN 1 AND 5) and the Mongo validator.
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                    "rating must be between " + MIN_RATING + " and " + MAX_RATING + ", was " + rating);
        }
        if (helpfulVotes < 0) {
            throw new IllegalArgumentException("helpfulVotes must not be negative");
        }
        criteria = (criteria == null) ? Map.of() : Map.copyOf(criteria);
    }

    /** A new, not-yet-persisted review. */
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

    /** Convenience for the UI: a 1-5 rating as stars. */
    public String stars() {
        return "*".repeat(rating) + "-".repeat(MAX_RATING - rating);
    }

    /** Objects.requireNonNull on the name keeps the denormalized copy honest. */
    public static Review requireNamed(Review review) {
        Objects.requireNonNull(review.reviewerName, "reviewerName");
        return review;
    }
}
