package org.example.bloggingplatformi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.model.RatingStats;
import org.example.bloggingplatformi.model.Review;

/** ReviewDao for PostgreSQL, using JDBC. */
public class JdbcReviewDao implements ReviewDao {

    private static final String SELECT_BASE = """
            SELECT r.review_id, r.post_id, r.reviewer_id, r.rating, r.headline,
                   r.body, r.created_at, r.updated_at,
                   u.display_name AS reviewer_name
              FROM reviews r
              JOIN users u ON u.user_id = r.reviewer_id
            """;

    @Override
    public String storeName() {
        return "PostgreSQL";
    }

    @Override
    public Optional<Review> findById(String reviewId) {
        Long id = parseId(reviewId);
        if (id == null) {
            return Optional.empty();
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement(SELECT_BASE + " WHERE r.review_id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Review>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public List<Review> findByPostId(long postId) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_BASE + " WHERE r.post_id = ? ORDER BY r.created_at DESC")) {
                ps.setLong(1, postId);
                return mapAll(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public List<Review> findByReviewer(long reviewerId, int limit) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_BASE + " WHERE r.reviewer_id = ? ORDER BY r.created_at DESC LIMIT ?")) {
                ps.setLong(1, reviewerId);
                ps.setInt(2, Math.max(1, limit));
                return mapAll(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public Optional<Review> findByPostAndReviewer(long postId, long reviewerId) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_BASE + " WHERE r.post_id = ? AND r.reviewer_id = ?")) {
                ps.setLong(1, postId);
                ps.setLong(2, reviewerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Review>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public Optional<Double> averageRating(long postId) {
        // The index on (post_id, rating) holds everything this query needs,
        // so PostgreSQL can answer it from the index alone.
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT AVG(rating) FROM reviews WHERE post_id = ?")) {
                ps.setLong(1, postId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double value = rs.getDouble(1);
                        // AVG of no rows is NULL, not 0.
                        return rs.wasNull() ? Optional.<Double>empty() : Optional.of(value);
                    }
                    return Optional.<Double>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public Map<Integer, Long> ratingDistribution(long postId) {
        String sql = """
                SELECT rating, COUNT(*) AS review_count
                  FROM reviews
                 WHERE post_id = ?
                 GROUP BY rating
                 ORDER BY rating DESC
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, postId);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<Integer, Long> distribution = new LinkedHashMap<>();
                    while (rs.next()) {
                        distribution.put(rs.getInt("rating"), rs.getLong("review_count"));
                    }
                    return distribution;
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public long countByPostId(long postId) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM reviews WHERE post_id = ?")) {
                ps.setLong(1, postId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public Map<Long, RatingStats> statsByPostIds(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        // One grouped query for all the posts. It can also be answered from
        // the (post_id, rating) index alone.
        String sql = """
                SELECT post_id, COUNT(*), ROUND(AVG(rating)::numeric, 2)
                  FROM reviews
                 WHERE post_id = ANY(?)
                 GROUP BY post_id
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setArray(1, connection.createArrayOf("bigint", postIds.toArray()));
                Map<Long, RatingStats> stats = new HashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stats.put(rs.getLong(1), new RatingStats(rs.getLong(2), rs.getBigDecimal(3)));
                    }
                }
                return stats;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public long count() {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM reviews");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public String insert(Review review) {
        String sql = """
                INSERT INTO reviews (post_id, reviewer_id, rating, headline, body)
                VALUES (?, ?, ?, ?, ?)
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, review.postId());
                ps.setLong(2, review.reviewerId());
                ps.setInt(3, review.rating());
                ps.setString(4, review.headline());
                ps.setString(5, review.body());
                ps.executeUpdate();
                return String.valueOf(SqlSupport.generatedKey(ps));
            } catch (SQLException e) {
                // A second review by the same user becomes a DuplicateEntityException.
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public boolean update(Review review) {
        Long id = parseId(review.reviewId());
        if (id == null) {
            return false;
        }
        String sql = "UPDATE reviews SET rating = ?, headline = ?, body = ? WHERE review_id = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, review.rating());
                ps.setString(2, review.headline());
                ps.setString(3, review.body());
                ps.setLong(4, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public boolean deleteById(String reviewId) {
        Long id = parseId(reviewId);
        if (id == null) {
            return false;
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement("DELETE FROM reviews WHERE review_id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    @Override
    public long deleteByPostId(long postId) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement("DELETE FROM reviews WHERE post_id = ?")) {
                ps.setLong(1, postId);
                return (long) ps.executeUpdate();
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Review");
            }
        });
    }

    private static Long parseId(String reviewId) {
        if (reviewId == null || reviewId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(reviewId.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Review> mapAll(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Review> reviews = new ArrayList<>();
            while (rs.next()) {
                reviews.add(map(rs));
            }
            return reviews;
        }
    }

    private static Review map(ResultSet rs) throws SQLException {
        return new Review(
                String.valueOf(rs.getLong("review_id")),
                rs.getLong("post_id"),
                rs.getLong("reviewer_id"),
                rs.getString("reviewer_name"),
                rs.getInt("rating"),
                rs.getString("headline"),
                rs.getString("body"),
                // The reviews table has no sub-score columns, so these are empty.
                Map.of(),
                0,
                SqlSupport.getOffsetDateTime(rs, "created_at"),
                SqlSupport.getOffsetDateTime(rs, "updated_at"));
    }
}
