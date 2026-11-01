package org.example.bloggingplatformi.db;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.example.bloggingplatformi.config.AppConfig;
import org.example.bloggingplatformi.exception.DataAccessException;

/**
 * Owns the PostgreSQL connection pool and the two ways to borrow from it.
 *
 * <p>A pool is not a premature optimisation here. Establishing a PostgreSQL
 * connection costs single-digit milliseconds including the TLS and
 * authentication handshake, which is more than most of the queries in this
 * application take to run. Opening one per query would mean the Phase 4
 * benchmarks measured connection setup rather than query execution, and every
 * indexing result would be buried in that noise.
 *
 * <p>Use {@link #withConnection} for a single statement and
 * {@link #inTransaction} when several writes must succeed or fail together.
 */
public final class Database {

    private static volatile HikariDataSource dataSource;

    private Database() {
    }

    /** A unit of work that borrows a connection and returns a result. */
    @FunctionalInterface
    public interface ConnectionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** A unit of work that borrows a connection and returns nothing. */
    @FunctionalInterface
    public interface ConnectionConsumer {
        void accept(Connection connection) throws SQLException;
    }

    public static DataSource dataSource() {
        HikariDataSource local = dataSource;
        if (local == null) {
            synchronized (Database.class) {
                local = dataSource;
                if (local == null) {
                    local = build();
                    dataSource = local;
                }
            }
        }
        return local;
    }

    private static HikariDataSource build() {
        AppConfig config = AppConfig.get();
        HikariConfig hikari = new HikariConfig();

        hikari.setJdbcUrl(config.getString("db.postgres.url"));
        hikari.setUsername(config.getString("db.postgres.user"));
        hikari.setPassword(config.getString("db.postgres.password"));
        hikari.setDriverClassName("org.postgresql.Driver");

        hikari.setMaximumPoolSize(config.getInt("db.postgres.pool.maxSize", 10));
        hikari.setMinimumIdle(config.getInt("db.postgres.pool.minIdle", 2));
        hikari.setConnectionTimeout(config.getLong("db.postgres.pool.connectionTimeoutMs", 10_000));
        hikari.setIdleTimeout(config.getLong("db.postgres.pool.idleTimeoutMs", 600_000));
        hikari.setMaxLifetime(config.getLong("db.postgres.pool.maxLifetimeMs", 1_800_000));
        hikari.setPoolName("blogging-platform-pool");

        // Server-side prepared statements. PostgreSQL caches the plan after a
        // statement has been executed a few times, which is most of the point
        // of using PreparedStatement in a loop.
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

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
     * Borrows a connection in auto-commit mode, runs the work and returns it.
     * The connection goes back to the pool whatever happens.
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
     * Runs the work inside a single transaction: commits on a clean return,
     * rolls back on any exception.
     *
     * <p>The rollback is in a nested try so that a failure while rolling back
     * cannot replace the exception that caused it. Losing the original cause
     * is how a one-line bug turns into an afternoon.
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
            rollbackQuietly(connection, e);
            throw translate(e);
        } catch (RuntimeException e) {
            rollbackQuietly(connection, e);
            throw e;
        } finally {
            if (connection != null) {
                try {
                    // Restore the pooled connection to the state the next
                    // borrower expects.
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException ignored) {
                    // Nothing useful to do; the pool evicts a broken connection.
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

    private static void rollbackQuietly(Connection connection, Exception primary) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            primary.addSuppressed(rollbackFailure);
        }
    }

    private static DataAccessException translate(SQLException e) {
        return new DataAccessException(
                "Database operation failed [SQLSTATE " + e.getSQLState() + "]: " + e.getMessage(), e);
    }

    /** True once the pool has been created; used by tests to avoid building one. */
    public static boolean isInitialised() {
        return dataSource != null;
    }

    /** Closes the pool. Called from the JavaFX {@code stop()} hook in Phase 3. */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }
}
