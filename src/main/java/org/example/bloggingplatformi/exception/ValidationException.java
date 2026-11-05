package org.example.bloggingplatformi.exception;

/**
 * Thrown by a service when the user typed something wrong, for example an empty title.
 * The message is shown to the user as it is.
 */
public class ValidationException extends RuntimeException {

    private final String field;

    public ValidationException(String message) {
        this(null, message);
    }

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
