package org.example.bloggingplatformi.benchmark;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.InsertManyOptions;

import org.bson.Document;
import org.bson.types.ObjectId;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.MongoProvider;

/**
 * Copies the benchmark comments and reviews from PostgreSQL into MongoDB, so
 * both databases are tested on the same data.
 *
 * Each document keeps its PostgreSQL id as sqlId, so a benchmark can find the
 * same comment in both. Comments are read in id order, so a parent is always
 * copied before its replies and a reply's path can be built from its parent's.
 */
final class MongoBenchLoader {

    private static final int BATCH = 5_000;

    private MongoBenchLoader() {
    }

    static void load() {
        MongoCollection<Document> comments = MongoProvider.comments();
        MongoCollection<Document> reviews = MongoProvider.reviews();

        System.out.println("Target MongoDB database: " + MongoProvider.database().getName());
        comments.deleteMany(new Document());
        reviews.deleteMany(new Document());

        Map<Long, String> names = loadUserNames();
        long start = System.nanoTime();
        long copiedComments = copyComments(comments, names);
        long copiedReviews = copyReviews(reviews, names);

        System.out.printf("Copied %,d comments and %,d reviews in %.1f s%n",
                copiedComments, copiedReviews, (System.nanoTime() - start) / 1e9);
        System.out.println("Now create the indexes: mongosh <uri>/blogging_platform_bench "
                + "--file db/mongo/02-indexes.js");
    }

    private static Map<Long, String> loadUserNames() {
        return Database.withConnection(connection -> {
            Map<Long, String> names = new HashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT user_id, display_name FROM users");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.put(rs.getLong(1), rs.getString(2));
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return names;
        });
    }

    /** Where a copied comment sits in its thread. Its replies build on this. */
    private record Placed(ObjectId id, String path, int depth) {
    }

    private static long copyComments(MongoCollection<Document> target, Map<Long, String> names) {
        String sql = """
                SELECT comment_id, post_id, author_id, parent_comment_id, body, status,
                       created_at, updated_at
                  FROM comments
                 ORDER BY comment_id
                """;
        return streamed(sql, rs -> {
            Map<Long, Placed> placed = new HashMap<>(800_000);
            List<Document> batch = new ArrayList<>(BATCH);
            long copied = 0;

            while (rs.next()) {
                long sqlId = rs.getLong("comment_id");
                long authorId = rs.getLong("author_id");
                long parentSqlId = rs.getLong("parent_comment_id");
                boolean isReply = !rs.wasNull();

                ObjectId id = new ObjectId();
                Placed parent = isReply ? placed.get(parentSqlId) : null;
                if (isReply && parent == null) {
                    throw new IllegalStateException("parent " + parentSqlId + " not yet copied");
                }
                String path = isReply ? parent.path() + parent.id().toHexString() + "/" : "/";
                int depth = isReply ? parent.depth() + 1 : 0;
                placed.put(sqlId, new Placed(id, path, depth));

                batch.add(new Document("_id", id)
                        .append("sqlId", sqlId)
                        .append("postId", rs.getLong("post_id"))
                        .append("authorId", authorId)
                        .append("authorName", names.getOrDefault(authorId, "[deleted user]"))
                        .append("parentId", isReply ? parent.id() : null)
                        .append("path", path)
                        .append("depth", depth)
                        .append("body", rs.getString("body"))
                        .append("status", rs.getString("status"))
                        .append("reactions", new Document())
                        .append("createdAt", new Date(rs.getTimestamp("created_at").getTime()))
                        .append("updatedAt", new Date(rs.getTimestamp("updated_at").getTime())));

                if (batch.size() == BATCH) {
                    copied += flush(target, batch);
                }
            }
            copied += flush(target, batch);
            return copied;
        });
    }

    private static long copyReviews(MongoCollection<Document> target, Map<Long, String> names) {
        String sql = """
                SELECT review_id, post_id, reviewer_id, rating, headline, body, created_at, updated_at
                  FROM reviews
                 ORDER BY review_id
                """;
        return streamed(sql, rs -> {
            List<Document> batch = new ArrayList<>(BATCH);
            long copied = 0;
            while (rs.next()) {
                long reviewerId = rs.getLong("reviewer_id");
                batch.add(new Document("_id", new ObjectId())
                        .append("sqlId", rs.getLong("review_id"))
                        .append("postId", rs.getLong("post_id"))
                        .append("reviewerId", reviewerId)
                        .append("reviewerName", names.getOrDefault(reviewerId, "[deleted user]"))
                        .append("rating", rs.getInt("rating"))
                        .append("headline", rs.getString("headline"))
                        .append("body", rs.getString("body"))
                        .append("helpfulVotes", 0)
                        .append("createdAt", new Date(rs.getTimestamp("created_at").getTime()))
                        .append("updatedAt", new Date(rs.getTimestamp("updated_at").getTime())));
                if (batch.size() == BATCH) {
                    copied += flush(target, batch);
                }
            }
            copied += flush(target, batch);
            return copied;
        });
    }

    private static long flush(MongoCollection<Document> target, List<Document> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        // Unordered insert is faster, and one bad document does not stop the rest.
        // A rejected document still throws an exception here.
        target.insertMany(batch, new InsertManyOptions().ordered(false));
        int size = batch.size();
        batch.clear();
        return size;
    }

    @FunctionalInterface
    private interface RowHandler {
        long handle(ResultSet rs) throws SQLException;
    }

    /**
     * Runs a query and reads the rows in chunks. Without autocommit off and a
     * fetch size, the PostgreSQL driver loads all rows into memory at once.
     */
    private static long streamed(String sql, RowHandler handler) {
        return Database.withConnection(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setFetchSize(10_000);
                    try (ResultSet rs = ps.executeQuery()) {
                        return handler.handle(rs);
                    }
                } finally {
                    connection.commit();
                    connection.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
