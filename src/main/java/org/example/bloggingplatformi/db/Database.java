package org.example.bloggingplatformi.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The connection to PostgreSQL with JDBC.
 * Opening a connection takes about 200 ms, while a simple query takes about 1 ms.
 * So the app opens one connection the first time it is needed and then reuses it.
 */
public final class Database {

    private static Connection connection;

    private Database() {
    }

    /** Returns the open connection, and opens it first if needed. Do not close it. */
    public static synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(
                    AppConfig.get("db.postgres.url"),
                    AppConfig.get("db.postgres.user"),
                    AppConfig.get("db.postgres.password"));
        }
        return connection;
    }

    /** Called when the app closes. */
    public static synchronized void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            // the app is closing anyway
        }
        connection = null;
    }
}
