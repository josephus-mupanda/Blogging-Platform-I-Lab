package org.example.bloggingplatformi.exception;

/**
 * Wraps a database error from JDBC or MongoDB. The service and UI code only
 * see this one type, so they do not need to know which database is used.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
