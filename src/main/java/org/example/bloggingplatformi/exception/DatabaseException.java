package org.example.bloggingplatformi.exception;

/**
 * Thrown by a DAO when the database fails, for example when PostgreSQL is not running.
 * It wraps the original SQLException, which Java forces us to catch.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage(), cause);
    }
}
