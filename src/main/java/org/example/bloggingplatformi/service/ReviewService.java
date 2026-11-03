package org.example.bloggingplatformi.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.dao.DaoFactory;
import org.example.bloggingplatformi.dao.ReviewDao;
import org.example.bloggingplatformi.exception.DuplicateEntityException;
import org.example.bloggingplatformi.exception.EntityNotFoundException;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Review;

/** Business rules for reviews: ratings from 1 to 5, and one review per user per post. */
public class ReviewService {

    private static final int MAX_HEADLINE = 150;
    private static final int MAX_BODY = 10_000;

    private final ReviewDao reviewDao;

    public ReviewService() {
        this(DaoFactory.reviews());
    }

    public ReviewService(ReviewDao reviewDao) {
        this.reviewDao = reviewDao;
    }

    public String activeStore() {
        return reviewDao.storeName();
    }

    public Optional<Review> findById(String reviewId) {
        return reviewDao.findById(reviewId);
    }

    public List<Review> findForPost(long postId) {
        return reviewDao.findByPostId(postId);
    }

    public List<Review> findByReviewer(long reviewerId, int limit) {
        return reviewDao.findByReviewer(reviewerId, limit);
    }

    public Optional<Review> findByPostAndReviewer(long postId, long reviewerId) {
        return reviewDao.findByPostAndReviewer(postId, reviewerId);
    }

    /** Empty when the post has no reviews, so the UI can show a dash instead of zero. */
    public Optional<Double> averageRating(long postId) {
        return reviewDao.averageRating(postId);
    }

    public Map<Integer, Long> ratingDistribution(long postId) {
        return reviewDao.ratingDistribution(postId);
    }

    public long countFor(long postId) {
        return reviewDao.countByPostId(postId);
    }

    public long count() {
        return reviewDao.count();
    }

    /**
     * Adds a review. The check here gives a clear message. The unique
     * constraint or index in the database still blocks a duplicate that slips past it.
     */
    public Review add(long postId, long reviewerId, String reviewerName,
                      int rating, String headline, String body) {
        validate(postId, reviewerId, rating, headline, body);

        if (reviewDao.findByPostAndReviewer(postId, reviewerId).isPresent()) {
            throw new DuplicateEntityException("review", "You have already reviewed this post.");
        }

        Review review = Review.of(postId, reviewerId, reviewerName, rating,
                trimOrNull(headline), trimOrNull(body));
        String id = reviewDao.insert(review);
        return review.withId(id);
    }

    public Review update(String reviewId, int rating, String headline, String body) {
        Review existing = reviewDao.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review", reviewId));

        ValidationException.Collector errors = new ValidationException.Collector();
        validateRating(rating, errors);
        validateText(headline, body, errors);
        errors.throwIfInvalid();

        Review updated = existing.withRating(rating, trimOrNull(headline), trimOrNull(body));
        if (!reviewDao.update(updated)) {
            throw new EntityNotFoundException("Review", reviewId);
        }
        return updated;
    }

    /**
     * Replaces a review's optional sub-scores. Only MongoDB stores them.
     * The PostgreSQL table has no column for them, so they come back empty.
     */
    public Review setCriteria(String reviewId, Map<String, Integer> criteria) {
        Review existing = reviewDao.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review", reviewId));

        if (criteria != null) {
            ValidationException.Collector errors = new ValidationException.Collector();
            criteria.forEach((key, value) -> errors.rejectIf(
                    value == null || value < Review.MIN_RATING || value > Review.MAX_RATING,
                    "criteria." + key,
                    "Scores must be between " + Review.MIN_RATING + " and " + Review.MAX_RATING + "."));
            errors.throwIfInvalid();
        }

        Review updated = existing.withCriteria(criteria);
        if (!reviewDao.update(updated)) {
            throw new EntityNotFoundException("Review", reviewId);
        }
        return updated;
    }

    public boolean delete(String reviewId) {
        return reviewDao.deleteById(reviewId);
    }

    /** Needed for MongoDB, because deleting a post in PostgreSQL does not remove its reviews there. */
    public long deleteForPost(long postId) {
        return reviewDao.deleteByPostId(postId);
    }

    private void validate(long postId, long reviewerId, int rating, String headline, String body) {
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.rejectIf(postId <= 0, "postId", "A valid post is required.");
        errors.rejectIf(reviewerId <= 0, "reviewerId", "A valid reviewer is required.");
        validateRating(rating, errors);
        validateText(headline, body, errors);
        errors.throwIfInvalid();
    }

    private void validateRating(int rating, ValidationException.Collector errors) {
        // Same rule as the database checks, but with a friendly message.
        errors.rejectIf(rating < Review.MIN_RATING || rating > Review.MAX_RATING,
                "rating",
                "Ratings must be between " + Review.MIN_RATING + " and " + Review.MAX_RATING + ".");
    }

    private void validateText(String headline, String body, ValidationException.Collector errors) {
        errors.rejectIf(headline != null && headline.strip().length() > MAX_HEADLINE,
                "headline", "Headlines must be at most " + MAX_HEADLINE + " characters.");
        errors.rejectIf(body != null && body.strip().length() > MAX_BODY,
                "body", "Reviews must be at most " + MAX_BODY + " characters.");
    }

    /** Turns blank text into null, so "no text" is always stored the same way. */
    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
