package org.example.bloggingplatformi.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.model.RatingStats;
import org.example.bloggingplatformi.model.Review;

/**
 * Reads and writes reviews. Like CommentDao, it has a PostgreSQL and a MongoDB
 * version, chosen by the app.comments.store setting.
 */
public interface ReviewDao {

    String storeName();

    Optional<Review> findById(String reviewId);

    List<Review> findByPostId(long postId);

    List<Review> findByReviewer(long reviewerId, int limit);

    /** The review this user left on this post, if there is one. */
    Optional<Review> findByPostAndReviewer(long postId, long reviewerId);

    /** Average rating for a post, or empty if it has no reviews. */
    Optional<Double> averageRating(long postId);

    /** Maps each rating value to how many reviews gave it. */
    Map<Integer, Long> ratingDistribution(long postId);

    long countByPostId(long postId);

    /**
     * Review count and average for many posts at once.
     * Posts with no reviews may be missing from the map. Both stores override
     * this default loop with one grouped query.
     */
    default Map<Long, RatingStats> statsByPostIds(Collection<Long> postIds) {
        Map<Long, RatingStats> stats = new HashMap<>();
        for (Long postId : postIds) {
            long count = countByPostId(postId);
            BigDecimal average = averageRating(postId)
                    .map(avg -> BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP))
                    .orElse(null);
            stats.put(postId, new RatingStats(count, average));
        }
        return stats;
    }

    long count();

    /**
     * Saves a new review. Throws DuplicateEntityException if the user already
     * reviewed this post (a UNIQUE constraint in PostgreSQL, a unique index in MongoDB).
     */
    String insert(Review review);

    boolean update(Review review);

    boolean deleteById(String reviewId);

    long deleteByPostId(long postId);
}
