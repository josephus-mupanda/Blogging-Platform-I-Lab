package org.example.bloggingplatformi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;

/**
 * JDBC implementation of {@link UserDao}.
 *
 * <p>Every statement is parameterized. The {@code LOWER(?)} comparisons pair
 * with the expression indexes in {@code V2__indexes.sql}: a lookup written as
 * {@code LOWER(username) = LOWER(?)} uses an index only if one was built on
 * that same expression, which is why those indexes exist in that exact form.
 */
public class JdbcUserDao implements UserDao {

    private static final String COLUMNS = """
            user_id, username, email, password_hash, display_name, bio,
            role, is_active, created_at, updated_at
            """;

    @Override
    public Optional<User> findById(long userId) {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE user_id = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<User>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE LOWER(username) = LOWER(?)";
        return findOneByString(sql, username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE LOWER(email) = LOWER(?)";
        return findOneByString(sql, email);
    }

    private Optional<User> findOneByString(String sql, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, value.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<User>empty();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public List<User> findAll(int limit, int offset) {
        String sql = "SELECT " + COLUMNS + """
                 FROM users
                 ORDER BY LOWER(username)
                 LIMIT ? OFFSET ?
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                ps.setInt(2, Math.max(0, offset));
                return mapAll(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public List<User> findAuthorsWithPosts() {
        // EXISTS rather than a JOIN with DISTINCT: the planner can stop at the
        // first matching post per user instead of building every pair and then
        // deduplicating them.
        String sql = "SELECT " + COLUMNS + """
                 FROM users u
                 WHERE EXISTS (SELECT 1 FROM posts p WHERE p.author_id = u.user_id)
                 ORDER BY LOWER(u.display_name)
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                return mapAll(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public boolean existsByUsername(String username) {
        return exists("SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)", username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return exists("SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)", email);
    }

    private boolean exists(String sql, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, value.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public long insert(User user) {
        // created_at and updated_at are left to their DEFAULT now(): the
        // database clock is the one that stays consistent across clients.
        String sql = """
                INSERT INTO users (username, email, password_hash, display_name, bio, role, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps =
                         connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.username().strip());
                ps.setString(2, user.email().strip().toLowerCase(Locale.ROOT));
                ps.setString(3, user.passwordHash());
                ps.setString(4, user.displayName().strip());
                ps.setString(5, user.bio());
                ps.setString(6, user.role().name());
                ps.setBoolean(7, user.active());
                ps.executeUpdate();
                return SqlSupport.generatedKey(ps);
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public boolean update(User user) {
        // password_hash is not updated here; changing a password is its own
        // operation with its own checks, not a side effect of editing a profile.
        String sql = """
                UPDATE users
                   SET username = ?, email = ?, display_name = ?, bio = ?, role = ?, is_active = ?
                 WHERE user_id = ?
                """;
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, user.username().strip());
                ps.setString(2, user.email().strip().toLowerCase(Locale.ROOT));
                ps.setString(3, user.displayName().strip());
                ps.setString(4, user.bio());
                ps.setString(5, user.role().name());
                ps.setBoolean(6, user.active());
                ps.setLong(7, user.userId());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public boolean deleteById(long userId) {
        String sql = "DELETE FROM users WHERE user_id = ?";
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, userId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    @Override
    public long count() {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) {
                throw SqlSupport.translate(e, "User");
            }
        });
    }

    private List<User> mapAll(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(map(rs));
            }
            return users;
        }
    }

    /** Maps the current row. Package-private so other DAOs can reuse it on joins. */
    static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("bio"),
                Role.from(rs.getString("role")),
                rs.getBoolean("is_active"),
                SqlSupport.getOffsetDateTime(rs, "created_at"),
                SqlSupport.getOffsetDateTime(rs, "updated_at"));
    }
}
