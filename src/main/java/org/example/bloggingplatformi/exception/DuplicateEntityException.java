package org.example.bloggingplatformi.exception;

/**
 * Thrown when a write would violate a uniqueness rule: a taken username or
 * email, a slug already in use, or a second review by the same user on the
 * same post.
 *
 * <p>The database enforces every one of these with a UNIQUE constraint, and
 * that enforcement is the one that counts. The service layer also checks up
 * front, purely so the user gets "that username is taken" instead of a
 * constraint name. The check is advisory; under concurrency the constraint
 * wins, and the DAO translates SQLSTATE 23505 into this same exception.
 */
public class DuplicateEntityException extends RuntimeException {

    private final String field;

    public DuplicateEntityException(String field, Object value) {
        super("A record already exists with " + field + " = " + value);
        this.field = field;
    }

    public DuplicateEntityException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    /** The conflicting field, so the UI can highlight the right input. */
    public String getField() {
        return field;
    }
}
