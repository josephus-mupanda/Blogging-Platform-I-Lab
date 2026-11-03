package org.example.bloggingplatformi.exception;

/**
 * Thrown when a value must be unique but is already used, for example a taken
 * username or a second review of the same post by the same user.
 * The services throw it with a clear message. The DAOs also throw it when a
 * unique constraint in the database fails.
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

    /** The field that clashed, so the UI can highlight the right input. */
    public String getField() {
        return field;
    }
}
