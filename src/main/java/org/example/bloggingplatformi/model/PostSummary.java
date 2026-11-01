package org.example.bloggingplatformi.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A row of the {@code v_post_summary} view: everything the list screen shows,
 * and nothing it does not.
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

    /** Null when the post has no reviews yet; the UI shows a dash. */
    public boolean hasRating() {
        return averageRating != null;
    }

    public String ratingDisplay() {
        return hasRating() ? averageRating.toPlainString() : "-";
    }
}
