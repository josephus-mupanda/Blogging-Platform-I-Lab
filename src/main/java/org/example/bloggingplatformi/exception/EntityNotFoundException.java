package org.example.bloggingplatformi.exception;

/**
 * Thrown by the services when a row or document they need does not exist.
 * DAO lookups return an Optional instead, because a missing row is normal in a search.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
