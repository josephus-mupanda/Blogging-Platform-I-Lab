package org.example.bloggingplatformi.dao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import org.bson.Document;
import org.bson.types.ObjectId;

import org.example.bloggingplatformi.db.MongoProvider;
import org.example.bloggingplatformi.exception.DataAccessException;
import org.example.bloggingplatformi.exception.DuplicateEntityException;
import org.example.bloggingplatformi.model.RatingStats;
import org.example.bloggingplatformi.model.Review;

/**
 * ReviewDao for MongoDB. Unlike the SQL table, each review can also hold
 * a criteria sub-document with extra scores. Averages and counts use the aggregation pipeline.
 */
public class MongoReviewDao implements ReviewDao {

    private static final String F_POST_ID = "postId";
    private static final String F_REVIEWER_ID = "reviewerId";
    private static final String F_REVIEWER_NAME = "reviewerName";
    private static final String F_RATING = "rating";
    private static final String F_HEADLINE = "headline";
    private static final String F_BODY = "body";
    private static final String F_CRITERIA = "criteria";
    private static final String F_HELPFUL = "helpfulVotes";
    private static final String F_CREATED_AT = "createdAt";
    private static final String F_UPDATED_AT = "updatedAt";

    private MongoCollection<Document> reviews() {
        return MongoProvider.reviews();
    }

    @Override
    public String storeName() {
        return "MongoDB";
    }

    @Override
    public Optional<Review> findById(String reviewId) {
        ObjectId id = parseId(reviewId);
        if (id == null) {
            return Optional.empty();
        }
        return run(() -> Optional.ofNullable(reviews().find(Filters.eq("_id", id)).first())
                .map(MongoReviewDao::map));
    }

    @Override
    public List<Review> findByPostId(long postId) {
        return run(() -> collect(reviews()
                .find(Filters.eq(F_POST_ID, postId))
                .sort(Sorts.descending(F_CREATED_AT))));
    }

    @Override
    public List<Review> findByReviewer(long reviewerId, int limit) {
        return run(() -> collect(reviews()
                .find(Filters.eq(F_REVIEWER_ID, reviewerId))
                .sort(Sorts.descending(F_CREATED_AT))
                .limit(Math.max(1, limit))));
    }

    @Override
    public Optional<Review> findByPostAndReviewer(long postId, long reviewerId) {
        return run(() -> Optional.ofNullable(reviews()
                .find(Filters.and(Filters.eq(F_POST_ID, postId),
                                  Filters.eq(F_REVIEWER_ID, reviewerId)))
                .first())
                .map(MongoReviewDao::map));
    }

    @Override
    public Optional<Double> averageRating(long postId) {
        return run(() -> {
            Document result = reviews().aggregate(List.of(
                    Aggregates.match(Filters.eq(F_POST_ID, postId)),
                    Aggregates.group(null, Accumulators.avg("average", "$" + F_RATING))
            )).first();

            // No reviews means no result document, like AVG returning NULL in SQL.
            if (result == null) {
                return Optional.<Double>empty();
            }
            Object average = result.get("average");
            return (average instanceof Number number)
                    ? Optional.of(number.doubleValue())
                    : Optional.<Double>empty();
        });
    }

    @Override
    public Map<Integer, Long> ratingDistribution(long postId) {
        return run(() -> {
            Map<Integer, Long> distribution = new LinkedHashMap<>();
            reviews().aggregate(List.of(
                    Aggregates.match(Filters.eq(F_POST_ID, postId)),
                    Aggregates.group("$" + F_RATING, Accumulators.sum("count", 1)),
                    Aggregates.sort(Sorts.descending("_id"))
            )).forEach(document -> {
                Object rating = document.get("_id");
                Object count = document.get("count");
                if (rating instanceof Number r && count instanceof Number c) {
                    distribution.put(r.intValue(), c.longValue());
                }
            });
            return distribution;
        });
    }

    @Override
    public long countByPostId(long postId) {
        return run(() -> reviews().countDocuments(Filters.eq(F_POST_ID, postId)));
    }

    @Override
    public Map<Long, RatingStats> statsByPostIds(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return run(() -> {
            Map<Long, RatingStats> stats = new HashMap<>();
            for (Document row : reviews().aggregate(List.of(
                    Aggregates.match(Filters.in(F_POST_ID, postIds)),
                    Aggregates.group("$" + F_POST_ID,
                            Accumulators.sum("n", 1),
                            Accumulators.avg("avg", "$" + F_RATING))))) {
                long postId = ((Number) row.get("_id")).longValue();
                long count = ((Number) row.get("n")).longValue();
                Number avg = (Number) row.get("avg");
                // Round to 2 places, the same as the SQL version, so both stores show the same number.
                BigDecimal average = avg == null ? null
                        : BigDecimal.valueOf(avg.doubleValue()).setScale(2, RoundingMode.HALF_UP);
                stats.put(postId, new RatingStats(count, average));
            }
            return stats;
        });
    }

    @Override
    public long count() {
        return run(() -> reviews().countDocuments());
    }

    @Override
    public String insert(Review review) {
        ObjectId id = new ObjectId();
        Date now = new Date();

        Document document = new Document("_id", id)
                .append(F_POST_ID, review.postId())
                .append(F_REVIEWER_ID, review.reviewerId())
                .append(F_REVIEWER_NAME, review.reviewerName())
                .append(F_RATING, review.rating())
                .append(F_HEADLINE, review.headline())
                .append(F_BODY, review.body())
                .append(F_HELPFUL, review.helpfulVotes())
                .append(F_CREATED_AT, now)
                .append(F_UPDATED_AT, now);

        if (!review.criteria().isEmpty()) {
            document.append(F_CRITERIA, new Document(new HashMap<>(review.criteria())));
        }

        try {
            reviews().insertOne(document);
            return id.toHexString();
        } catch (MongoWriteException e) {
            // A unique index on (postId, reviewerId) blocks a second review by the same user.
            if (e.getError().getCategory() == com.mongodb.ErrorCategory.DUPLICATE_KEY) {
                throw new DuplicateEntityException("review",
                        "This user has already reviewed this post.", e);
            }
            throw new DataAccessException("MongoDB review insert failed: " + e.getMessage(), e);
        } catch (MongoException e) {
            throw new DataAccessException("MongoDB review insert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Review review) {
        ObjectId id = parseId(review.reviewId());
        if (id == null) {
            return false;
        }
        return run(() -> {
            List<org.bson.conversions.Bson> updates = new ArrayList<>(List.of(
                    Updates.set(F_RATING, review.rating()),
                    Updates.set(F_HEADLINE, review.headline()),
                    Updates.set(F_BODY, review.body()),
                    Updates.set(F_UPDATED_AT, new Date())));

            if (review.criteria().isEmpty()) {
                updates.add(Updates.unset(F_CRITERIA));
            } else {
                updates.add(Updates.set(F_CRITERIA, new Document(new HashMap<>(review.criteria()))));
            }

            return reviews().updateOne(Filters.eq("_id", id), Updates.combine(updates))
                    .getModifiedCount() > 0;
        });
    }

    @Override
    public boolean deleteById(String reviewId) {
        ObjectId id = parseId(reviewId);
        if (id == null) {
            return false;
        }
        return run(() -> reviews().deleteOne(Filters.eq("_id", id)).getDeletedCount() > 0);
    }

    @Override
    public long deleteByPostId(long postId) {
        return run(() -> reviews().deleteMany(Filters.eq(F_POST_ID, postId)).getDeletedCount());
    }

    private static List<Review> collect(FindIterable<Document> documents) {
        List<Review> reviews = new ArrayList<>();
        for (Document document : documents) {
            reviews.add(map(document));
        }
        return reviews;
    }

    static Review map(Document document) {
        Map<String, Integer> criteria = new HashMap<>();
        Document stored = document.get(F_CRITERIA, Document.class);
        if (stored != null) {
            stored.forEach((key, value) -> {
                if (value instanceof Number number) {
                    criteria.put(key, number.intValue());
                }
            });
        }

        return new Review(
                document.getObjectId("_id").toHexString(),
                toLong(document.get(F_POST_ID)),
                toLong(document.get(F_REVIEWER_ID)),
                document.getString(F_REVIEWER_NAME),
                toInt(document.get(F_RATING)),
                document.getString(F_HEADLINE),
                document.getString(F_BODY),
                criteria,
                toInt(document.get(F_HELPFUL)),
                toOffsetDateTime(document.getDate(F_CREATED_AT)),
                toOffsetDateTime(document.getDate(F_UPDATED_AT)));
    }

    private static long toLong(Object value) {
        return (value instanceof Number number) ? number.longValue() : 0L;
    }

    private static int toInt(Object value) {
        return (value instanceof Number number) ? number.intValue() : 0;
    }

    private static OffsetDateTime toOffsetDateTime(Date date) {
        return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static ObjectId parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return ObjectId.isValid(id.strip()) ? new ObjectId(id.strip()) : null;
    }

    private static <T> T run(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (MongoException e) {
            throw new DataAccessException("MongoDB review operation failed: " + e.getMessage(), e);
        }
    }
}
