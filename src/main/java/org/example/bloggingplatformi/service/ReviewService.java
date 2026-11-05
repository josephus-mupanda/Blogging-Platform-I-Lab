package org.example.bloggingplatformi.service;

import java.util.List;

import org.example.bloggingplatformi.dao.ReviewDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Review;
import org.example.bloggingplatformi.model.User;

/** Business rules for reviews (stored in MongoDB). */
public class ReviewService {

    private final ReviewDao reviewDao = new ReviewDao();
    private final UserService userService = new UserService();

    public List<Review> forPost(long postId) {
        return reviewDao.findByPost(postId);
    }

    /** A user can review a post only once, with a rating from 1 to 5. */
    public void add(long postId, User reviewer, int rating, String text) {
        if (rating < 1 || rating > 5) {
            throw new ValidationException("The rating must be between 1 and 5.");
        }
        userService.requireActive(reviewer);
        if (reviewDao.exists(postId, reviewer.id())) {
            throw new ValidationException("You have already reviewed this post.");
        }
        reviewDao.insert(postId, reviewer.id(), reviewer.displayName(), rating, text == null ? "" : text.strip());
    }

    /** The average rating, or 0 when there is no review. */
    public double average(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Review review : reviews) {
            total += review.rating();
        }
        return (double) total / reviews.size();
    }

    public long count() {
        return reviewDao.count();
    }
}
