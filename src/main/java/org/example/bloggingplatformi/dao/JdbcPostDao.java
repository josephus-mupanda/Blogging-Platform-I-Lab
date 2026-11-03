package org.example.bloggingplatformi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.PostSummary;

/**
 * PostDao for PostgreSQL, using JDBC.
 * The search query is built from fixed SQL pieces. User values are always bound
 * as ? parameters, and the ORDER BY text comes from the PostSort enum.
 */
public class JdbcPostDao implements PostDao {

    private static final String POST_COLUMNS = """
            post_id, author_id, title, slug, body, excerpt, status,
            view_count, published_at, created_at, updated_at
            """;

    private static final String SUMMARY_COLUMNS = """
            p.post_id, p.title, p.slug, p.excerpt, p.status, p.view_count,
            p.published_at, p.created_at,
            u.user_id AS author_id, u.username AS author_username,
            u.display_name AS author_display_name
            """;

    // Comment and review counts are not selected here. They may live in MongoDB,
    // so PostService fills them in from the active store.

    @Override
    public Optional<Post> findById(long postId) {
        String sql = "SELECT " + POST_COLUMNS + " FROM posts WHERE post_id = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, postId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapPost(rs)) : Optional.<Post>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    @Override
    public Optional<Post> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT " + POST_COLUMNS + " FROM posts WHERE slug = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, slug.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapPost(rs)) : Optional.<Post>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    @Override
    public Page<PostSummary> search(PostFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (filter.hasKeyword()) {
            // ILIKE finds part of a word in the title. Full-text search finds
            // whole words in the title or body.
            // The tsvector expression must match idx_posts_search_tsv exactly,
            // or PostgreSQL will not use the index.
            where.append("""
                     AND (p.title ILIKE ?
                          OR (setweight(to_tsvector('english', coalesce(p.title, '')), 'A') ||
                              setweight(to_tsvector('english', coalesce(p.body, '')), 'B'))
                             @@ plainto_tsquery('english', ?))
                    """);
            params.add("%" + filter.keyword() + "%");
            params.add(filter.keyword());
        }
        if (filter.authorId() != null) {
            where.append(" AND p.author_id = ?");
            params.add(filter.authorId());
        }
        if (filter.status() != null) {
            where.append(" AND p.status = ?");
            params.add(filter.status().name());
        }
        if (filter.tagId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM post_tags pt"
                       + " WHERE pt.post_id = p.post_id AND pt.tag_id = ?)");
            params.add(filter.tagId());
        }

        String countSql = "SELECT COUNT(*) FROM posts p JOIN users u ON u.user_id = p.author_id"
                + where;

        String pageSql = "SELECT " + SUMMARY_COLUMNS
                + " FROM posts p JOIN users u ON u.user_id = p.author_id"
                + where
                + " ORDER BY " + filter.sort().sqlFragment()
                + " LIMIT ? OFFSET ?";

        return Database.withConnection(connection -> {
            long total = countMatching(connection, countSql, params);
            if (total == 0) {
                return Page.<PostSummary>empty(filter.pageNumber(), filter.pageSize());
            }
            try (PreparedStatement ps = connection.prepareStatement(pageSql)) {
                int index = bind(ps, params);
                ps.setInt(index++, filter.pageSize());
                ps.setLong(index, filter.offset());

                try (ResultSet rs = ps.executeQuery()) {
                    List<PostSummary> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(mapSummary(rs));
                    }
                    return new Page<>(rows, filter.pageNumber(), filter.pageSize(), total);
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    private long countMatching(Connection connection, String sql, List<Object> params)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Binds the filter values to the ? placeholders, in order.
     * setObject works for String, Long and Integer values.
     *
     * @return the next free parameter index, for LIMIT and OFFSET
     */
    private int bind(PreparedStatement ps, List<Object> params) throws SQLException {
        int index = 1;
        for (Object value : params) {
            ps.setObject(index, value);
            index++;
        }
        return index;
    }

    @Override
    public List<PostSummary> findByAuthor(long authorId, int limit, int offset) {
        String sql = "SELECT " + SUMMARY_COLUMNS + """
                 FROM posts p
                 JOIN users u ON u.user_id = p.author_id
                 WHERE p.author_id = ?
                 ORDER BY p.created_at DESC, p.post_id DESC
                 LIMIT ? OFFSET ?
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, authorId);
                ps.setInt(2, Math.max(1, limit));
                ps.setInt(3, Math.max(0, offset));
                try (ResultSet rs = ps.executeQuery()) {
                    List<PostSummary> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(mapSummary(rs));
                    }
                    return rows;
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    @Override
    public boolean existsBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement("SELECT 1 FROM posts WHERE slug = ?")) {
                ps.setString(1, slug.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    @Override
    public long insertWithTags(Post post, Set<Integer> tagIds) {
        String sql = """
                INSERT INTO posts (author_id, title, slug, body, excerpt, status, view_count, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return Database.inTransaction(connection -> {
            long postId;
            try (PreparedStatement ps =
                         connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, post.authorId());
                ps.setString(2, post.title().strip());
                ps.setString(3, post.slug());
                ps.setString(4, post.body());
                ps.setString(5, post.excerpt());
                ps.setString(6, post.status().name());
                ps.setInt(7, post.viewCount());
                SqlSupport.setNullableTimestamp(ps, 8, post.publishedAt());
                ps.executeUpdate();
                postId = SqlSupport.generatedKey(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }

            if (tagIds != null && !tagIds.isEmpty()) {
                assignTags(connection, postId, tagIds);
            }
            return postId;
        });
    }

    @Override
    public boolean updateWithTags(Post post, Set<Integer> tagIds) {
        String sql = """
                UPDATE posts
                   SET title = ?, body = ?, excerpt = ?, status = ?, published_at = ?
                 WHERE post_id = ?
                """;
        // The slug is not updated, so links to the post keep working after a title change.
        return Database.inTransaction(connection -> {
            boolean changed;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, post.title().strip());
                ps.setString(2, post.body());
                ps.setString(3, post.excerpt());
                ps.setString(4, post.status().name());
                SqlSupport.setNullableTimestamp(ps, 5, post.publishedAt());
                ps.setLong(6, post.postId());
                changed = ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }

            if (tagIds != null) {
                try (PreparedStatement clear =
                             connection.prepareStatement("DELETE FROM post_tags WHERE post_id = ?")) {
                    clear.setLong(1, post.postId());
                    clear.executeUpdate();
                } catch (SQLException e) {
                    throw SqlSupport.translate(e, "Post tags");
                }
                if (!tagIds.isEmpty()) {
                    assignTags(connection, post.postId(), tagIds);
                }
            }
            return changed;
        });
    }

    /** Inserts all tag links in one batch instead of one query per tag. */
    private void assignTags(Connection connection, long postId, Set<Integer> tagIds)
            throws SQLException {
        String sql = "INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Integer tagId : tagIds) {
                ps.setLong(1, postId);
                ps.setInt(2, tagId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public boolean deleteById(long postId) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement("DELETE FROM posts WHERE post_id = ?")) {
                ps.setLong(1, postId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    @Override
    public boolean incrementViewCount(long postId) {
        String sql = "UPDATE posts SET view_count = view_count + 1 WHERE post_id = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, postId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    @Override
    public long count() {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM posts");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    @Override
    public long countByStatus(String status) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement("SELECT COUNT(*) FROM posts WHERE status = ?")) {
                ps.setString(1, status);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Post");
            }
        });
    }

    static Post mapPost(ResultSet rs) throws SQLException {
        return Post.builder()
                .postId(rs.getLong("post_id"))
                .authorId(rs.getLong("author_id"))
                .title(rs.getString("title"))
                .slug(rs.getString("slug"))
                .body(rs.getString("body"))
                .excerpt(rs.getString("excerpt"))
                .status(PostStatus.from(rs.getString("status")))
                .viewCount(rs.getInt("view_count"))
                .publishedAt(SqlSupport.getOffsetDateTime(rs, "published_at"))
                .createdAt(SqlSupport.getOffsetDateTime(rs, "created_at"))
                .updatedAt(SqlSupport.getOffsetDateTime(rs, "updated_at"))
                .build();
    }

    static PostSummary mapSummary(ResultSet rs) throws SQLException {
        return new PostSummary(
                rs.getLong("post_id"),
                rs.getString("title"),
                rs.getString("slug"),
                rs.getString("excerpt"),
                PostStatus.from(rs.getString("status")),
                rs.getInt("view_count"),
                SqlSupport.getOffsetDateTime(rs, "published_at"),
                SqlSupport.getOffsetDateTime(rs, "created_at"),
                rs.getLong("author_id"),
                rs.getString("author_username"),
                rs.getString("author_display_name"),
                0L, 0L, null);
    }
}
