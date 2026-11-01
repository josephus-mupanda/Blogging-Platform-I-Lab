package org.example.bloggingplatformi.support;

import java.sql.Connection;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.MongoProvider;

/**
 * Detects whether the databases are reachable, so integration tests can skip
 * instead of fail when they are not.
 *
 * <p>A skipped test is honest about what was and was not verified. A test that
 * fails because nothing is running trains everyone to ignore red builds, and a
 * test that silently passes without touching a database verifies nothing at
 * all. JUnit's assumption mechanism reports these as skipped, which is the
 * accurate answer.
 *
 * <p>The result is cached: probing a dead port costs the connection timeout,
 * and paying that once per test class is already enough.
 */
public final class Databases {

    private static Boolean postgresAvailable;
    private static Boolean mongoAvailable;

    private Databases() {
    }

    public static synchronized boolean postgresAvailable() {
        if (postgresAvailable == null) {
            postgresAvailable = probePostgres();
        }
        return postgresAvailable;
    }

    public static synchronized boolean mongoAvailable() {
        if (mongoAvailable == null) {
            mongoAvailable = probeMongo();
        }
        return mongoAvailable;
    }

    private static boolean probePostgres() {
        try (Connection connection = Database.dataSource().getConnection()) {
            return connection.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean probeMongo() {
        try {
            return MongoProvider.isReachable();
        } catch (Exception e) {
            return false;
        }
    }

    public static String postgresSkipReason() {
        return "PostgreSQL is not reachable. Start the server and run the scripts in "
             + "db/postgres, then re-run this test.";
    }

    public static String mongoSkipReason() {
        return "MongoDB is not reachable. Start mongod and run the scripts in "
             + "db/mongo, then re-run this test.";
    }
}
