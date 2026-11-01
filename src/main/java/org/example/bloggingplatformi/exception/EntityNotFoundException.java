package org.example.bloggingplatformi.exception;

/**
 * Thrown when an operation targets a row or document that does not exist.
 *
 * <p>DAO lookups return {@link java.util.Optional} rather than throwing: an
 * absent row is a normal outcome of a search. This exception is for the
 * service layer, where "update post 97" with no post 97 is a genuine error.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
