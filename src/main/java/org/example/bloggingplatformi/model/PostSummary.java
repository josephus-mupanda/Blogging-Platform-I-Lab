package org.example.bloggingplatformi.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row of a post listing: everything the list screen shows, and nothing it
 * does not.
 *
 * <p>The post columns come from PostgreSQL; the comment and review figures are
 * filled in by {@code PostService} from whichever store holds comments and
 * reviews, and are {@link #UNKNOWN} when that store cannot be reached.
 *
 * <p>Deliberately excludes {@code posts.body}. Pulling full article text for
 * every row of a twenty-row page is the single easiest way to make a listing
 * slow, and the precomputed {@code excerpt} exists exactly so it is not needed.
 * The full {@link Post} is loaded only when one is opened.
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
     * Marks a comment or review count that could not be read, because the
     * store holding comments and reviews was unreachable. Shown as a dash:
     * zero would be a claim, and a wrong one.
     */
    public static final long UNKNOWN = -1;

    /**
     * Copy with the engagement figures from the comment and review store.
     *
     * <p>Those figures live wherever {@code app.comments.store} points, which
     * is not necessarily PostgreSQL, so the post query cannot compute them.
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

    /** Null when the post has no reviews yet; the UI shows a dash. */
    public boolean hasRating() {
        return averageRating != null;
    }

    public String ratingDisplay() {
        return hasRating() ? averageRating.toPlainString() : "-";
    }
}
