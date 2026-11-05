package org.example.bloggingplatformi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.exception.DatabaseException;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;

/**
 * Reads and writes the users table with JDBC.
 * Every query uses a PreparedStatement with ? placeholders, so a value typed by the
 * user can never change the SQL (no SQL injection).
 */
public class UserDao {

    /** Returns the user, or null if nobody has this username. */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            statement.setString(1, username);
            ResultSet rows = statement.executeQuery();
            return rows.next() ? toUser(rows) : null;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the user", e);
        }
    }

    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY username";
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            ResultSet rows = statement.executeQuery();
            List<User> users = new ArrayList<>();
            while (rows.next()) {
                users.add(toUser(rows));
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the users", e);
        }
    }

    /** The users who can write posts, for the "author" filter of the post list. */
    public List<User> findAuthors() {
        String sql = "SELECT * FROM users WHERE role IN ('ADMIN', 'AUTHOR') ORDER BY display_name";
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            ResultSet rows = statement.executeQuery();
            List<User> users = new ArrayList<>();
            while (rows.next()) {
                users.add(toUser(rows));
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the authors", e);
        }
    }

    public boolean usernameExists(String username) {
        return exists("SELECT 1 FROM users WHERE username = ?", username);
    }

    public boolean emailExists(String email) {
        return exists("SELECT 1 FROM users WHERE email = ?", email);
    }

    public boolean isActive(long userId) {
        try (PreparedStatement statement = Database.connection().prepareStatement(
                "SELECT 1 FROM users WHERE user_id = ? AND active")) {
            statement.setLong(1, userId);
            return statement.executeQuery().next();
        } catch (SQLException e) {
            throw new DatabaseException("Could not check account status", e);
        }
    }

    public boolean setActive(long userId, boolean active) {
        try (PreparedStatement statement = Database.connection().prepareStatement(
                "UPDATE users SET active = ? WHERE user_id = ? AND role <> 'ADMIN'")) {
            statement.setBoolean(1, active);
            statement.setLong(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Could not update account status", e);
        }
    }

    private boolean exists(String sql, String value) {
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            statement.setString(1, value);
            return statement.executeQuery().next();
        } catch (SQLException e) {
            throw new DatabaseException("Could not check the user", e);
        }
    }

    public void insert(String username, String email, String passwordHash, String displayName, Role role) {
        String sql = "INSERT INTO users (username, email, password_hash, display_name, role) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = Database.connection().prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, displayName);
            statement.setString(5, role.name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not create the account", e);
        }
    }

    public int count() {
        try (PreparedStatement statement = Database.connection().prepareStatement("SELECT COUNT(*) FROM users")) {
            ResultSet rows = statement.executeQuery();
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new DatabaseException("Could not count the users", e);
        }
    }

    /** Turns the current row of the result into a User object. */
    private User toUser(ResultSet row) throws SQLException {
        return new User(
                row.getLong("user_id"),
                row.getString("username"),
                row.getString("email"),
                row.getString("password_hash"),
                row.getString("display_name"),
                Role.valueOf(row.getString("role")),
                row.getBoolean("active"),
                row.getTimestamp("created_at").toLocalDateTime());
    }
}
