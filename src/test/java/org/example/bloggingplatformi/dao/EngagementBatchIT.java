package org.example.bloggingplatformi.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.MongoProvider;
import org.example.bloggingplatformi.model.RatingStats;
import org.example.bloggingplatformi.support.Databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The batched engagement lookups behind the post list must agree, post for
 * post, with the single-post queries behind the detail screen, in both stores.
 *
 * <p>A disagreement here is exactly the defect these methods were written to
 * fix: a list that says one number and a detail page that says another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EngagementBatchIT {

    /** Seeded posts, plus one id that has no comments or reviews at all. */
    private static final List<Long> POST_IDS =
            LongStream.concat(LongStream.rangeClosed(1, 40), LongStream.of(9_999_999L)).boxed().toList();

    @AfterAll
    void shutdown() {
        if (Database.isInitialised()) {
            Database.shutdown();
        }
        if (MongoProvider.isInitialised()) {
            MongoProvider.shutdown();
        }
    }

    @Test
    @DisplayName("PostgreSQL: batched comment counts match per-post counts")
    void jdbcComments() {
        assumeTrue(Databases.postgresAvailable(), Databases.postgresSkipReason());
        assertCommentCountsAgree(new JdbcCommentDao());
    }

    @Test
    @DisplayName("MongoDB: batched comment counts match per-post counts")
    void mongoComments() {
        assumeTrue(Databases.mongoAvailable(), Databases.mongoSkipReason());
        assertCommentCountsAgree(new MongoCommentDao());
    }

    @Test
    @DisplayName("PostgreSQL: batched rating stats match per-post stats")
    void jdbcReviews() {
        assumeTrue(Databases.postgresAvailable(), Databases.postgresSkipReason());
        assertRatingStatsAgree(new JdbcReviewDao());
    }

    @Test
    @DisplayName("MongoDB: batched rating stats match per-post stats")
    void mongoReviews() {
        assumeTrue(Databases.mongoAvailable(), Databases.mongoSkipReason());
        assertRatingStatsAgree(new MongoReviewDao());
    }

    @Test
    @DisplayName("an empty page asks nothing of either store")
    void emptyInput() {
        assertTrue(new JdbcCommentDao().countVisibleByPostIds(List.of()).isEmpty());
        assertTrue(new MongoReviewDao().statsByPostIds(List.of()).isEmpty());
    }

    private static void assertCommentCountsAgree(CommentDao dao) {
        Map<Long, Long> batched = dao.countVisibleByPostIds(POST_IDS);
        long nonZero = 0;
        for (long postId : POST_IDS) {
            long single = dao.countByPostId(postId, false);
            assertEquals(single, batched.getOrDefault(postId, 0L), dao.storeName() + " post " + postId);
            nonZero += single > 0 ? 1 : 0;
        }
        assertTrue(nonZero > 10, "the sample actually exercises seeded data");
    }

    private static void assertRatingStatsAgree(ReviewDao dao) {
        Map<Long, RatingStats> batched = dao.statsByPostIds(POST_IDS);
        for (long postId : POST_IDS) {
            RatingStats stats = batched.getOrDefault(postId, RatingStats.NONE);
            assertEquals(dao.countByPostId(postId), stats.count(), dao.storeName() + " count, post " + postId);

            BigDecimal expected = dao.averageRating(postId)
                    .map(avg -> BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP))
                    .orElse(null);
            if (expected == null) {
                assertEquals(null, stats.average(), dao.storeName() + " average, post " + postId);
            } else {
                assertEquals(0, expected.compareTo(stats.average()),
                        dao.storeName() + " average, post " + postId
                        + ": expected " + expected + " but was " + stats.average());
            }
        }
    }
}
