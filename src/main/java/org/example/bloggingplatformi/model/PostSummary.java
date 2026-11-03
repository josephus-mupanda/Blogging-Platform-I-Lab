package org.example.bloggingplatformi.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row of the post list, with only the fields the list shows.
 * It leaves out the post body to keep the list fast. The excerpt is shown instead.
 * PostService fills in the comment and review numbers later.
 */
public record PostSummary(
        long postId,
        String title,
        String slug,
        String excerpt,
        PostStatus status,
        int viewCount,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        long authorId,
        String authorUsername,
        String authorDisplayName,
        long commentCount,
        long reviewCount,
        BigDecimal averageRating
) {

    /**
     * Used when the comment or review count could not be read because that
     * database was down. The UI shows a dash, because zero would be wrong.
     */
    public static final long UNKNOWN = -1;

    /**
     * Returns a copy with the comment count and rating filled in. These can come
     * from MongoDB, so the PostgreSQL post query cannot work them out itself.
     */
    public PostSummary withEngagement(long comments, RatingStats ratings) {
        return new PostSummary(postId, title, slug, excerpt, status, viewCount, publishedAt,
                createdAt, authorId, authorUsername, authorDisplayName,
                comments, ratings.count(), ratings.average());
    }

    public PostSummary withEngagementUnavailable() {
        return new PostSummary(postId, title, slug, excerpt, status, viewCount, publishedAt,
                createdAt, authorId, authorUsername, authorDisplayName,
                UNKNOWN, UNKNOWN, null);
    }

    public boolean hasEngagement() {
        return commentCount != UNKNOWN;
    }

    /** False when the post has no reviews yet. The UI then shows a dash. */
    public boolean hasRating() {
        return averageRating != null;
    }

    public String ratingDisplay() {
        return hasRating() ? averageRating.toPlainString() : "-";
    }
}
