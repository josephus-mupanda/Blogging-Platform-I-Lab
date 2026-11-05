package org.example.bloggingplatformi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.exception.DatabaseException;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.postgresql.util.PSQLException;

/** Reads and writes the posts table (and the post_tags links) with JDBC. */
public class PostDao {

    /** Every post query reads the post and its author's name with a JOIN. */
    private static final String SELECT_POSTS =
            "SELECT p.*, u.display_name AS author_name FROM posts p JOIN users u ON u.user_id = p.author_id";

    /** Returns the post, or null if it does not exist. */
    public Post findById(long postId) {
        try {
            return findById(Database.connection(), postId);
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the post", e);
        }
    }

    private Post findById(Connection connection, long postId) throws SQLException {
        String sql = SELECT_POSTS + " WHERE p.post_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, postId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? toPost(rows) : null;
            }
        }
    }

    /**
     * Search with filters and pagination. Each filter is optional (null = not used).
     * keyword: case-insensitive search in the title and the text (ILIKE).
     * limit / offset: one page, for example 10 posts starting at post 20.
     */
    public List<Post> search(String keyword, Long authorId, Long tagId, int limit, int offset) {
        List<Object> values = new ArrayList<>();
        String sql = SELECT_POSTS + " WHERE 1 = 1" + filters(keyword, authorId, tagId, values)
                + " ORDER BY p.created_at DESC, p.post_id DESC LIMIT ? OFFSET ?";
        values.add(limit);
        values.add(offset);

        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i)); // the ? are numbered from 1
            }
            ResultSet rows = statement.executeQuery();
            List<Post> posts = new ArrayList<>();
            while (rows.next()) {
                posts.add(toPost(rows));
            }
            return posts;
        } catch (SQLException e) {
            throw new DatabaseException("Could not search the posts", e);
        }
    }

    /** How many posts match the same filters, to know how many pages there are. */
    public int count(String keyword, Long authorId, Long tagId) {
        List<Object> values = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM posts p WHERE 1 = 1" + filters(keyword, authorId, tagId, values);
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            ResultSet rows = statement.executeQuery();
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new DatabaseException("Could not count the posts", e);
        }
    }

    /**
     * Builds the "AND ..." part of the WHERE for the filters that are set,
     * and adds their values to the list in the same order as the ? placeholders.
     */
    private String filters(String keyword, Long authorId, Long tagId, List<Object> values) {
        String sql = "";
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND (p.title ILIKE ? OR p.body ILIKE ?)";
            String pattern = "%" + keyword.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            values.add(pattern);
            values.add(pattern);
        }
        if (authorId != null) {
            sql += " AND p.author_id = ?";
            values.add(authorId);
        }
        if (tagId != null) {
            sql += " AND p.post_id IN (SELECT post_id FROM post_tags WHERE tag_id = ?)";
            values.add(tagId);
        }
        return sql;
    }

    /**
     * Saves a new post and its tags in one transaction:
     * either both are saved, or nothing is (rollback on error).
     * Returns the saved post with its database-generated fields.
     */
    public Post insert(long authorId, String title, String body, List<Long> tagIds) {
        List<Long> tags = List.copyOf(tagIds);
        String sql = "INSERT INTO posts (author_id, title, body) VALUES (?, ?, ?) RETURNING post_id";
        try {
            Connection connection = Database.connection();
            connection.setAutoCommit(false); // start the transaction
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                checkUniqueTitle(connection, title, null);
                statement.setLong(1, authorId);
                statement.setString(2, title);
                statement.setString(3, body);
                ResultSet rows = statement.executeQuery();
                rows.next();
                long postId = rows.getLong(1);

                saveTags(connection, postId, tags);
                Post saved = findById(connection, postId);
                connection.commit();     // save everything
                return saved;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();   // undo everything
                throw e;
            } finally {
                connection.setAutoCommit(true); // back to normal: each query saved at once
            }
        } catch (SQLException e) {
            throw postWriteError("Could not save the post", e);
        }
    }

    /** Updates a post and replaces its tags, in one transaction. */
    public Post update(long postId, String title, String body, List<Long> tagIds) {
        List<Long> tags = List.copyOf(tagIds);
        String sql = "UPDATE posts SET title = ?, body = ?, updated_at = now() WHERE post_id = ?";
        try {
            Connection connection = Database.connection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 PreparedStatement removeTags = connection.prepareStatement("DELETE FROM post_tags WHERE post_id = ?")) {
                checkUniqueTitle(connection, title, postId);
                statement.setString(1, title);
                statement.setString(2, body);
                statement.setLong(3, postId);
                if (statement.executeUpdate() == 0) {
                    throw new SQLException("The post no longer exists.");
                }

                removeTags.setLong(1, postId);
                removeTags.executeUpdate();
                saveTags(connection, postId, tags);
                Post saved = findById(connection, postId);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw postWriteError("Could not update the post", e);
        }
    }

    private void checkUniqueTitle(Connection connection, String title, Long postId) throws SQLException {
        // Lock only this normalized title so older databases also reject concurrent duplicate saves.
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(10553, hashtext(lower(btrim(?))))")) {
            lock.setString(1, title);
            lock.execute();
        }
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT 1 FROM posts WHERE lower(btrim(title)) = lower(btrim(?)) AND post_id IS DISTINCT FROM ? LIMIT 1")) {
            check.setString(1, title);
            check.setObject(2, postId, Types.BIGINT);
            try (ResultSet rows = check.executeQuery()) {
                if (rows.next()) {
                    throw duplicateTitle();
                }
            }
        }
    }

    private RuntimeException postWriteError(String message, SQLException error) {
        if (error instanceof PSQLException postgresError && "23505".equals(error.getSQLState())
                && postgresError.getServerErrorMessage() != null
                && "uq_posts_normalized_title".equals(postgresError.getServerErrorMessage().getConstraint())) {
            return duplicateTitle();
        }
        return new DatabaseException(message, error);
    }

    private ValidationException duplicateTitle() {
        return new ValidationException("title", "That title is already in use. Choose a different title.");
    }

    private void saveTags(Connection connection, long postId, List<Long> tagIds) throws SQLException {
        String sql = "INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (long tagId : tagIds) {
                statement.setLong(1, postId);
                statement.setLong(2, tagId);
                statement.executeUpdate();
            }
        }
    }

    /** Deletes a post. Its post_tags rows go too (ON DELETE CASCADE). */
    public void delete(long postId) {
        try (PreparedStatement statement = Database.connection().prepareStatement("DELETE FROM posts WHERE post_id = ?")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the post", e);
        }
    }

    /** Turns the current row of the result into a Post object. */
    private Post toPost(ResultSet row) throws SQLException {
        return new Post(
                row.getLong("post_id"),
                row.getLong("author_id"),
                row.getString("author_name"),
                row.getString("title"),
                row.getString("body"),
                row.getTimestamp("created_at").toLocalDateTime(),
                row.getTimestamp("updated_at").toLocalDateTime());
    }
}
