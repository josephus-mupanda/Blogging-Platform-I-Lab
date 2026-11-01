package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.model.Review;

/**
 * Data access for reviews. Like {@link CommentDao} this has a relational and a
 * document implementation, selected by {@code app.comments.store}.
 */
public interface ReviewDao {

    String storeName();

    Optional<Review> findById(String reviewId);

    List<Review> findByPostId(long postId);

    List<Review> findByReviewer(long reviewerId, int limit);

    /** The one review this user left on this post, if any. */
    Optional<Review> findByPostAndReviewer(long postId, long reviewerId);

    /** Mean rating for a post, or empty when it has no reviews. */
    Optional<Double> averageRating(long postId);

    /** Rating value to number of reviews, for the distribution chart. */
    Map<Integer, Long> ratingDistribution(long postId);

    long countByPostId(long postId);

    long count();

    /**
     * @throws org.example.bloggingplatformi.exception.DuplicateEntityException
     *         if this user has already reviewed this post. Both stores enforce
     *         it: a UNIQUE constraint in PostgreSQL, a unique index in MongoDB.
     */
    String insert(Review review);

    boolean update(Review review);

    boolean deleteById(String reviewId);

    long deleteByPostId(long postId);
}
