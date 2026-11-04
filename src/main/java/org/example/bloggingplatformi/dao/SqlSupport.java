package org.example.bloggingplatformi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;

import org.example.bloggingplatformi.exception.DataAccessException;
import org.example.bloggingplatformi.exception.DuplicateEntityException;

/**
 * Small JDBC helpers used by all the Jdbc DAOs: reading nullable columns,
 * getting generated keys, and turning SQL errors into app exceptions.
 * All queries use PreparedStatement parameters, never user input pasted into SQL.
 */
final class SqlSupport {

    // PostgreSQL error codes (SQLSTATE) for constraint failures.
    static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";

    static final String SQLSTATE_CHECK_VIOLATION = "23514";

    static final String SQLSTATE_NOT_NULL_VIOLATION = "23502";

    private SqlSupport() {
    }

    // getLong and getInt return 0 for SQL NULL, so wasNull() is needed
    // to tell a real zero from a missing value.

    static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Reads a TIMESTAMPTZ column as OffsetDateTime.
     * java.sql.Timestamp has no time zone, so the offset would be lost.
     */
    static OffsetDateTime getOffsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    /** Binds a Long that may be null. */
    static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    static void setNullableTimestamp(PreparedStatement ps, int index, OffsetDateTime value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, value);
        }
    }

    /**
     * Returns the id the database generated for the last insert.
     * The statement must be created with {@link Statement#RETURN_GENERATED_KEYS}.
     */
    static long generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        throw new DataAccessException("Insert succeeded but returned no generated key");
    }

    /**
     * Turns a SQLException into an exception the UI can show to the user.
     * The constraint names come from V1__schema.sql, where every constraint is named.
     */
    static RuntimeException translate(SQLException e, String entity) {
        if (SQLSTATE_UNIQUE_VIOLATION.equals(e.getSQLState())) {
            String field = fieldFromConstraint(e.getMessage());
            return new DuplicateEntityException(field,
                    "That " + field + " is already in use.", e);
        }
        if (SQLSTATE_FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
            return new DataAccessException(
                    "Referenced record does not exist, or is still referenced elsewhere: "
                    + e.getMessage(), e);
        }
        if (SQLSTATE_CHECK_VIOLATION.equals(e.getSQLState())
                || SQLSTATE_NOT_NULL_VIOLATION.equals(e.getSQLState())) {
            return new DataAccessException(
                    "The value rejected by the database was invalid: " + e.getMessage(), e);
        }
        return new DataAccessException(
                entity + " operation failed [SQLSTATE " + e.getSQLState() + "]: " + e.getMessage(), e);
    }

    /** Maps a constraint name to a field name the user will understand. */
    private static String fieldFromConstraint(String message) {
        if (message == null) {
            return "value";
        }
        String lower = message.toLowerCase();
        if (lower.contains("uq_users_username")) {
            return "username";
        }
        if (lower.contains("uq_users_email")) {
            return "email";
        }
        if (lower.contains("uq_posts_slug")) {
            return "slug";
        }
        if (lower.contains("uq_tags_name") || lower.contains("uq_tags_slug")) {
            return "tag name";
        }
        if (lower.contains("uq_reviews_post_reviewer")) {
            return "review";
        }
        if (lower.contains("pk_post_tags")) {
            return "tag assignment";
        }
        return "value";
    }
}
