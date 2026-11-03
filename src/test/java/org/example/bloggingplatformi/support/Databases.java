package org.example.bloggingplatformi.support;

import java.sql.Connection;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.MongoProvider;

/**
 * Checks if PostgreSQL and MongoDB are running, so integration tests can be
 * skipped instead of failing when they are not.
 * The answer is saved after the first check, because a failed check waits for a timeout.
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
