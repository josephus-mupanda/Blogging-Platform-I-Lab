package org.example.bloggingplatformi.exception;

/**
 * Wraps a checked {@link java.sql.SQLException} or a MongoDB driver failure so
 * that the service and UI layers never import JDBC or Mongo types.
 *
 * <p>That boundary is the point of the whole DAO layer: the comment store can
 * be swapped between PostgreSQL and MongoDB without the layers above noticing,
 * and that is only true if the failure types are shared too.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
