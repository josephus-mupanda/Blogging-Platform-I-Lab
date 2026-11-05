package org.example.bloggingplatformi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.exception.DatabaseException;
import org.example.bloggingplatformi.model.ArchivedItem;
import org.example.bloggingplatformi.model.Tag;

/** Reads and writes the tags table with JDBC. */
public class TagDao {

    public List<Tag> findAll() {
        return query("SELECT * FROM tags WHERE deleted_at IS NULL", null);
    }

    /** The tags of one post: a JOIN through the post_tags link table. */
    public List<Tag> findByPost(long postId) {
        return query("SELECT t.* FROM tags t JOIN post_tags pt ON pt.tag_id = t.tag_id"
                + " WHERE pt.post_id = ? AND t.deleted_at IS NULL ORDER BY t.name", postId);
    }

    private List<Tag> query(String sql, Long postId) {
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            if (postId != null) {
                statement.setLong(1, postId);
            }
            ResultSet rows = statement.executeQuery();
            List<Tag> tags = new ArrayList<>();
            while (rows.next()) {
                tags.add(new Tag(rows.getLong("tag_id"), rows.getString("name")));
            }
            return tags;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the tags", e);
        }
    }

    /** Returns false if another tag already uses this name. */
    public boolean insert(String name) {
        try (PreparedStatement insert = Database.connection().prepareStatement(
                "INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO NOTHING")) {
            insert.setString(1, name);
            return insert.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the tag", e);
        }
    }

    public boolean isArchivedName(String name) {
        try (PreparedStatement statement = Database.connection().prepareStatement(
                "SELECT 1 FROM tags WHERE name = ? AND deleted_at IS NOT NULL")) {
            statement.setString(1, name);
            return statement.executeQuery().next();
        } catch (SQLException e) {
            throw new DatabaseException("Could not check the tag", e);
        }
    }

    public boolean archive(long tagId) {
        return changeStatus(tagId, "UPDATE tags SET deleted_at = now() WHERE tag_id = ? AND deleted_at IS NULL");
    }

    public boolean restore(long tagId) {
        return changeStatus(tagId, "UPDATE tags SET deleted_at = NULL WHERE tag_id = ? AND deleted_at IS NOT NULL");
    }

    private boolean changeStatus(long tagId, String sql) {
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            statement.setLong(1, tagId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Could not change the tag status", e);
        }
    }

    public List<ArchivedItem> archived() {
        try (PreparedStatement statement = Database.connection().prepareStatement(
                "SELECT tag_id, name, deleted_at FROM tags WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, tag_id");
             ResultSet rows = statement.executeQuery()) {
            List<ArchivedItem> items = new ArrayList<>();
            while (rows.next()) {
                items.add(new ArchivedItem(ArchivedItem.Kind.TAG, rows.getString("tag_id"), rows.getString("name"),
                        "Restoring this tag also restores its visibility on linked posts.",
                        rows.getTimestamp("deleted_at").toLocalDateTime(), null));
            }
            return items;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load tags in trash", e);
        }
    }

    /**
     * For each tag, how many posts use it, most used first.
     * LEFT JOIN, so a tag used by no post is still listed, with 0.
     */
    public Map<String, Integer> usage() {
        String sql = "SELECT t.name, COUNT(p.post_id) AS posts FROM tags t"
                + " LEFT JOIN post_tags pt ON pt.tag_id = t.tag_id"
                + " LEFT JOIN posts p ON p.post_id = pt.post_id AND p.deleted_at IS NULL"
                + " WHERE t.deleted_at IS NULL"
                + " GROUP BY t.name ORDER BY posts DESC, t.name";
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            ResultSet rows = statement.executeQuery();
            Map<String, Integer> usage = new LinkedHashMap<>(); // keeps the order of the query
            while (rows.next()) {
                usage.put(rows.getString("name"), rows.getInt("posts"));
            }
            return usage;
        } catch (SQLException e) {
            throw new DatabaseException("Could not count the tags", e);
        }
    }
}
