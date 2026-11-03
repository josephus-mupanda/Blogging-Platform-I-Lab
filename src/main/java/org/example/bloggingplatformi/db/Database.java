package org.example.bloggingplatformi.db;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.example.bloggingplatformi.config.AppConfig;
import org.example.bloggingplatformi.exception.DataAccessException;

/**
 * Holds the PostgreSQL connection pool. Opening a new connection takes longer
 * than most of our queries, so connections are reused.
 * Use withConnection for one statement, and inTransaction when several
 * writes must all succeed or all fail.
 */
public final class Database {

    private static HikariDataSource dataSource;

    private Database() {
    }

    /** Database work that uses a connection and returns a result. */
    @FunctionalInterface
    public interface ConnectionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Database work that uses a connection and returns nothing. */
    @FunctionalInterface
    public interface ConnectionConsumer {
        void accept(Connection connection) throws SQLException;
    }

    // Creates the pool the first time it is needed. synchronized means two
    // threads can never both create a pool.
    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            dataSource = build();
        }
        return dataSource;
    }

    private static HikariDataSource build() {
        AppConfig config = AppConfig.get();
        HikariConfig hikari = new HikariConfig();

        hikari.setJdbcUrl(config.getString("db.postgres.url"));
        hikari.setUsername(config.getString("db.postgres.user"));
        hikari.setPassword(config.getString("db.postgres.password"));
        hikari.setDriverClassName("org.postgresql.Driver");

        // At most this many connections are open at the same time.
        hikari.setMaximumPoolSize(config.getInt("db.postgres.pool.maxSize", 10));

        try {
            return new HikariDataSource(hikari);
        } catch (RuntimeException e) {
            throw new DataAccessException(
                    "Could not initialise the PostgreSQL connection pool. "
                    + "Check db.postgres.url, the username and the password, and that the "
                    + "server is running on the configured port.", e);
        }
    }

    /**
     * Takes a connection from the pool, runs the work and returns the result.
     * The connection always goes back to the pool, even after an error.
     */
    public static <T> T withConnection(ConnectionFunction<T> work) {
        try (Connection connection = dataSource().getConnection()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    public static void withConnection(ConnectionConsumer work) {
        withConnection(connection -> {
            work.accept(connection);
            return null;
        });
    }

    /**
     * Runs the work in one transaction. It commits if the work finishes and rolls back on any exception.
     * If the rollback also fails, the first exception is still the one thrown.
     */
    public static <T> T inTransaction(ConnectionFunction<T> work) {
        Connection connection = null;
        try {
            connection = dataSource().getConnection();
            connection.setAutoCommit(false);

            T result = work.apply(connection);

            connection.commit();
            return result;
        } catch (SQLException e) {
            rollback(connection);
            throw translate(e);
        } catch (RuntimeException e) {
            rollback(connection);
            throw e;
        } finally {
            if (connection != null) {
                try {
                    // Turn auto-commit back on before the connection goes back to the pool.
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException ignored) {
                    // Nothing to do here. The pool throws away a broken connection.
                }
            }
        }
    }

    public static void inTransaction(ConnectionConsumer work) {
        inTransaction(connection -> {
            work.accept(connection);
            return null;
        });
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original error is the one worth showing, so this one is ignored.
        }
    }

    private static DataAccessException translate(SQLException e) {
        return new DataAccessException(
                "Database operation failed [SQLSTATE " + e.getSQLState() + "]: " + e.getMessage(), e);
    }

    /** True once the pool has been created. Tests use it to avoid creating one. */
    public static synchronized boolean isInitialised() {
        return dataSource != null;
    }

    /** Closes the pool. Called when the JavaFX app stops. */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }
}
