package org.example.bloggingplatformi.model;

import java.math.BigDecimal;

/**
 * Review count and average rating for one post.
 *
 * @param average rounded to two places; null when there are no reviews
 */
public record RatingStats(long count, BigDecimal average) {

    public static final RatingStats NONE = new RatingStats(0, null);
}
