package org.example.bloggingplatformi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.util.Slugs;

/** TagDao for PostgreSQL, using JDBC. */
public class JdbcTagDao implements TagDao {

    private static final String COLUMNS = "tag_id, name, slug, created_at";

    @Override
    public Optional<Tag> findById(int tagId) {
        String sql = "SELECT " + COLUMNS + " FROM tags WHERE tag_id = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, tagId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Tag>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public Optional<Tag> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        // The name is lower-cased in Java. LOWER(name) matches the idx_tags_name_lower index.
        String sql = "SELECT " + COLUMNS + " FROM tags WHERE LOWER(name) = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name.strip().toLowerCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Tag>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public List<Tag> findAll() {
        // No ORDER BY: TagService sorts the list with its own merge sort.
        String sql = "SELECT " + COLUMNS + " FROM tags";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                return mapAll(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public List<Tag> findByPostId(long postId) {
        String sql = """
                SELECT t.tag_id, t.name, t.slug, t.created_at
                  FROM tags t
                  JOIN post_tags pt ON pt.tag_id = t.tag_id
                 WHERE pt.post_id = ?
                 ORDER BY t.name
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, postId);
                return mapAll(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public List<Tag> searchByPrefix(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        // "prefix%" can use the idx_tags_name_lower index.
        // escapeLike stops a % or _ typed by the user from acting as a wildcard.
        String sql = "SELECT " + COLUMNS + """
                 FROM tags
                 WHERE LOWER(name) LIKE ? ESCAPE '\\'
                 ORDER BY name
                 LIMIT ?
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, escapeLike(prefix.strip().toLowerCase(Locale.ROOT)) + "%");
                ps.setInt(2, Math.max(1, limit));
                return mapAll(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public Map<String, Long> usageCounts(int limit) {
        String sql = """
                SELECT t.name, COUNT(pt.post_id) AS post_count
                  FROM tags t
                  LEFT JOIN post_tags pt ON pt.tag_id = t.tag_id
                 GROUP BY t.tag_id, t.name
                 ORDER BY post_count DESC, t.name
                 LIMIT ?
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    // LinkedHashMap keeps the order from the ORDER BY.
                    Map<String, Long> counts = new LinkedHashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getString("name"), rs.getLong("post_count"));
                    }
                    return counts;
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public long insert(Tag tag) {
        String sql = "INSERT INTO tags (name, slug) VALUES (?, ?)";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, tag.name());
                ps.setString(2, tag.slug());
                ps.executeUpdate();
                return SqlSupport.generatedKey(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public boolean deleteById(int tagId) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement("DELETE FROM tags WHERE tag_id = ?")) {
                ps.setInt(1, tagId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public int findOrCreate(String name) {
        String normalized = name.strip().toLowerCase(Locale.ROOT);

        // One statement, so two users adding the same tag at once cannot clash.
        // If the tag already exists, the insert returns nothing and the second
        // SELECT finds the existing id.
        String sql = """
                WITH inserted AS (
                    INSERT INTO tags (name, slug) VALUES (?, ?)
                    ON CONFLICT (name) DO NOTHING
                    RETURNING tag_id
                )
                SELECT tag_id FROM inserted
                UNION ALL
                SELECT tag_id FROM tags WHERE name = ?
                LIMIT 1
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, normalized);
                ps.setString(2, Slugs.forTag(normalized));
                ps.setString(3, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("tag_id");
                    }
                }
                throw new org.example.bloggingplatformi.exception.DataAccessException(
                        "Could not resolve or create tag: " + normalized);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    @Override
    public long count() {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM tags");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "Tag");
            }
        });
    }

    /** Escapes \, % and _ so they are matched as normal characters in LIKE. */
    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }

    private List<Tag> mapAll(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Tag> tags = new ArrayList<>();
            while (rs.next()) {
                tags.add(map(rs));
            }
            return tags;
        }
    }

    static Tag map(ResultSet rs) throws SQLException {
        return new Tag(
                rs.getInt("tag_id"),
                rs.getString("name"),
                rs.getString("slug"),
                SqlSupport.getOffsetDateTime(rs, "created_at"));
    }
}
