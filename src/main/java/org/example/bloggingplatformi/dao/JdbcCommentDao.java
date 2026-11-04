package org.example.bloggingplatformi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;

/**
 * CommentDao for PostgreSQL. Each comment stores its parent_comment_id,
 * and recursive queries (WITH RECURSIVE) walk the reply tree.
 * Depth is worked out in the query, not stored like in MongoCommentDao.
 */
public class JdbcCommentDao implements CommentDao {

    private static final String SELECT_BASE = """
            SELECT c.comment_id, c.post_id, c.author_id, c.parent_comment_id,
                   c.body, c.status, c.created_at, c.updated_at,
                   u.display_name AS author_name
              FROM comments c
              LEFT JOIN users u ON u.user_id = c.author_id
            """;

    @Override
    public String storeName() {
        return "PostgreSQL";
    }

    @Override
    public Optional<Comment> findById(String commentId) {
        Long id = parseId(commentId);
        if (id == null) {
            return Optional.empty();
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement(SELECT_BASE + " WHERE c.comment_id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs, 0)) : Optional.<Comment>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public List<Comment> findByPostId(long postId, boolean includeHidden) {
        // The recursive query works out each comment's depth and a sort path.
        // Sorting by sort_path puts each reply right under its parent.
        String sql = """
                WITH RECURSIVE thread AS (
                    SELECT c.comment_id, c.post_id, c.author_id, c.parent_comment_id,
                           c.body, c.status, c.created_at, c.updated_at,
                           0 AS depth,
                           ARRAY[c.created_at] AS sort_path,
                           ''::text AS ancestor_path
                      FROM comments c
                     WHERE c.post_id = ? AND c.parent_comment_id IS NULL
                    UNION ALL
                    SELECT c.comment_id, c.post_id, c.author_id, c.parent_comment_id,
                           c.body, c.status, c.created_at, c.updated_at,
                           t.depth + 1,
                           t.sort_path || c.created_at,
                           t.ancestor_path || t.comment_id::text || '/'
                      FROM comments c
                      JOIN thread t ON c.parent_comment_id = t.comment_id
                )
                SELECT t.*, u.display_name AS author_name
                  FROM thread t
                  LEFT JOIN users u ON u.user_id = t.author_id
                 WHERE (? OR t.status = 'VISIBLE')
                 ORDER BY t.sort_path, t.comment_id
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, postId);
                ps.setBoolean(2, includeHidden);
                return mapThread(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public List<Comment> findReplies(String parentId) {
        Long id = parseId(parentId);
        if (id == null) {
            return List.of();
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_BASE + " WHERE c.parent_comment_id = ? ORDER BY c.created_at")) {
                ps.setLong(1, id);
                return mapAll(ps, 0);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public List<Comment> findSubtree(String rootCommentId) {
        Long id = parseId(rootCommentId);
        if (id == null) {
            return List.of();
        }
        String sql = """
                WITH RECURSIVE subtree AS (
                    SELECT c.comment_id, c.post_id, c.author_id, c.parent_comment_id,
                           c.body, c.status, c.created_at, c.updated_at, 0 AS depth
                      FROM comments c
                     WHERE c.comment_id = ?
                    UNION ALL
                    SELECT c.comment_id, c.post_id, c.author_id, c.parent_comment_id,
                           c.body, c.status, c.created_at, c.updated_at, s.depth + 1
                      FROM comments c
                      JOIN subtree s ON c.parent_comment_id = s.comment_id
                )
                SELECT s.*, u.display_name AS author_name
                  FROM subtree s
                  LEFT JOIN users u ON u.user_id = s.author_id
                 ORDER BY s.depth, s.created_at
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, id);
                return mapThread(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public List<Comment> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        // ILIKE '%word%' can only use a trigram index (idx_comments_body_trgm).
        // Without it, every comment row would be scanned.
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_BASE + " WHERE c.body ILIKE ? ORDER BY c.created_at DESC LIMIT ?")) {
                ps.setString(1, "%" + keyword.strip() + "%");
                ps.setInt(2, Math.max(1, limit));
                return mapAll(ps, 0);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public List<Comment> findForModeration(int limit) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_BASE + " WHERE c.status <> 'VISIBLE' ORDER BY c.created_at DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, limit));
                return mapAll(ps, 0);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public long countByPostId(long postId, boolean includeHidden) {
        String sql = "SELECT COUNT(*) FROM comments WHERE post_id = ? AND (? OR status = 'VISIBLE')";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, postId);
                ps.setBoolean(2, includeHidden);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public Map<Long, Long> countVisibleByPostIds(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        // ANY(?) takes the whole list of ids as one array parameter.
        String sql = """
                SELECT post_id, COUNT(*)
                  FROM comments
                 WHERE status = 'VISIBLE' AND post_id = ANY(?)
                 GROUP BY post_id
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setArray(1, connection.createArrayOf("bigint", postIds.toArray()));
                Map<Long, Long> counts = new HashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        counts.put(rs.getLong(1), rs.getLong(2));
                    }
                }
                return counts;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public long count() {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM comments");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public String insert(Comment comment) {
        String sql = """
                INSERT INTO comments (post_id, author_id, parent_comment_id, body, status)
                VALUES (?, ?, ?, ?, ?)
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, comment.postId());
                SqlSupport.setNullableLong(ps, 2, comment.authorId());
                SqlSupport.setNullableLong(ps, 3, parseId(comment.parentId()));
                ps.setString(4, comment.body());
                ps.setString(5, comment.status().name());
                ps.executeUpdate();
                return String.valueOf(SqlSupport.generatedKey(ps));
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public boolean updateBody(String commentId, String newBody) {
        Long id = parseId(commentId);
        if (id == null) {
            return false;
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE comments SET body = ? WHERE comment_id = ?")) {
                ps.setString(1, newBody);
                ps.setLong(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public boolean updateStatus(String commentId, CommentStatus status) {
        Long id = parseId(commentId);
        if (id == null) {
            return false;
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE comments SET status = ? WHERE comment_id = ?")) {
                ps.setString(1, status.name());
                ps.setLong(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public long deleteWithReplies(String commentId) {
        Long id = parseId(commentId);
        if (id == null) {
            return 0;
        }
        // Count the comment and its replies first, so we return the same number
        // as MongoCommentDao. Deleting the root removes the replies by ON DELETE CASCADE.
        return Database.inTransaction(connection -> {
            long affected = 0;
            String countSql = """
                    WITH RECURSIVE subtree AS (
                        SELECT comment_id FROM comments WHERE comment_id = ?
                        UNION ALL
                        SELECT c.comment_id FROM comments c
                          JOIN subtree s ON c.parent_comment_id = s.comment_id
                    )
                    SELECT COUNT(*) FROM subtree
                    """;
            try (PreparedStatement ps = connection.prepareStatement(countSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        affected = rs.getLong(1);
                    }
                }
            }
            try (PreparedStatement ps =
                         connection.prepareStatement("DELETE FROM comments WHERE comment_id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0 ? affected : 0L;
            }
        });
    }

    @Override
    public long deleteByPostId(long postId) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement("DELETE FROM comments WHERE post_id = ?")) {
                ps.setLong(1, postId);
                return (long) ps.executeUpdate();
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Comment");
            }
        });
    }

    @Override
    public long renameAuthor(long authorId, String newDisplayName) {
        // Nothing to do. The name is only stored in users and read with a join.
        return 0;
    }

    /** Turns the String id into a Long, or null if it is not a number. */
    private static Long parseId(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(commentId.strip());
        } catch (NumberFormatException e) {
            // For example a MongoDB id. Treat it as "not found" instead of crashing.
            return null;
        }
    }

    private List<Comment> mapAll(PreparedStatement ps, int depth) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Comment> comments = new ArrayList<>();
            while (rs.next()) {
                comments.add(map(rs, depth));
            }
            return comments;
        }
    }

    /** Like mapAll, but reads depth from the recursive query's depth column. */
    private List<Comment> mapThread(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Comment> comments = new ArrayList<>();
            while (rs.next()) {
                comments.add(map(rs, rs.getInt("depth")));
            }
            return comments;
        }
    }

    private static Comment map(ResultSet rs, int depth) throws SQLException {
        Long parentId = SqlSupport.getNullableLong(rs, "parent_comment_id");
        return new Comment(
                String.valueOf(rs.getLong("comment_id")),
                rs.getLong("post_id"),
                SqlSupport.getNullableLong(rs, "author_id"),
                rs.getString("author_name"),
                parentId == null ? null : String.valueOf(parentId),
                Comment.ROOT_PATH,
                depth,
                rs.getString("body"),
                CommentStatus.from(rs.getString("status")),
                Map.of(),
                SqlSupport.getOffsetDateTime(rs, "created_at"),
                SqlSupport.getOffsetDateTime(rs, "updated_at"));
    }
}
