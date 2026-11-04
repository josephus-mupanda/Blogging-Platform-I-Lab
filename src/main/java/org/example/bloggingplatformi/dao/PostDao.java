package org.example.bloggingplatformi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.exception.DatabaseException;
import org.example.bloggingplatformi.model.Post;

/** Reads and writes the posts table (and the post_tags links) with JDBC. */
public class PostDao {

    /** Every post query reads the post and its author's name with a JOIN. */
    private static final String SELECT_POSTS =
            "SELECT p.*, u.display_name AS author_name FROM posts p JOIN users u ON u.user_id = p.author_id";

    /** Returns the post, or null if it does not exist. */
    public Post findById(long postId) {
        String sql = SELECT_POSTS + " WHERE p.post_id = ?";
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            statement.setLong(1, postId);
            ResultSet rows = statement.executeQuery();
            return rows.next() ? toPost(rows) : null;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the post", e);
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
                + " ORDER BY p.created_at DESC LIMIT ? OFFSET ?";
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
            values.add("%" + keyword.strip() + "%");
            values.add("%" + keyword.strip() + "%");
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
     * Returns the id PostgreSQL gave to the new post.
     */
    public long insert(long authorId, String title, String body, List<Long> tagIds) {
        String sql = "INSERT INTO posts (author_id, title, body) VALUES (?, ?, ?) RETURNING post_id";
        try {
            Connection connection = Database.connection();
            connection.setAutoCommit(false); // start the transaction
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, authorId);
                statement.setString(2, title);
                statement.setString(3, body);
                ResultSet rows = statement.executeQuery();
                rows.next();
                long postId = rows.getLong(1);

                saveTags(connection, postId, tagIds);
                connection.commit();     // save everything
                return postId;
            } catch (SQLException e) {
                connection.rollback();   // undo everything
                throw e;
            } finally {
                connection.setAutoCommit(true); // back to normal: each query saved at once
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the post", e);
        }
    }

    /** Updates a post and replaces its tags, in one transaction. */
    public void update(long postId, String title, String body, List<Long> tagIds) {
        String sql = "UPDATE posts SET title = ?, body = ?, updated_at = now() WHERE post_id = ?";
        try {
            Connection connection = Database.connection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 PreparedStatement removeTags = connection.prepareStatement("DELETE FROM post_tags WHERE post_id = ?")) {
                statement.setString(1, title);
                statement.setString(2, body);
                statement.setLong(3, postId);
                statement.executeUpdate();

                removeTags.setLong(1, postId);
                removeTags.executeUpdate();
                saveTags(connection, postId, tagIds);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the post", e);
        }
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
