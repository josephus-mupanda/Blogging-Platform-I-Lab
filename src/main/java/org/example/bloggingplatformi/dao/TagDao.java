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
import org.example.bloggingplatformi.model.Tag;

/** Reads and writes the tags table with JDBC. */
public class TagDao {

    public List<Tag> findAll() {
        return query("SELECT * FROM tags", null);
    }

    /** The tags of one post: a JOIN through the post_tags link table. */
    public List<Tag> findByPost(long postId) {
        return query("SELECT t.* FROM tags t JOIN post_tags pt ON pt.tag_id = t.tag_id"
                + " WHERE pt.post_id = ? ORDER BY t.name", postId);
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

    /**
     * Returns the id of the tag with this name, and creates the tag first if it does not exist.
     * ON CONFLICT DO NOTHING: if the name is already there, the INSERT does nothing.
     */
    public long findOrCreate(String name) {
        try (PreparedStatement insert = Database.connection().prepareStatement(
                     "INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO NOTHING");
             PreparedStatement select = Database.connection().prepareStatement("SELECT tag_id FROM tags WHERE name = ?")) {
            insert.setString(1, name);
            insert.executeUpdate();
            select.setString(1, name);
            ResultSet rows = select.executeQuery();
            rows.next();
            return rows.getLong(1);
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the tag", e);
        }
    }

    /** Deletes a tag. It is removed from its posts too (ON DELETE CASCADE on post_tags). */
    public void delete(long tagId) {
        try (PreparedStatement statement = Database.connection().prepareStatement("DELETE FROM tags WHERE tag_id = ?")) {
            statement.setLong(1, tagId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the tag", e);
        }
    }

    /**
     * For each tag, how many posts use it, most used first.
     * LEFT JOIN, so a tag used by no post is still listed, with 0.
     */
    public Map<String, Integer> usage() {
        String sql = "SELECT t.name, COUNT(pt.post_id) AS posts FROM tags t"
                + " LEFT JOIN post_tags pt ON pt.tag_id = t.tag_id"
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
