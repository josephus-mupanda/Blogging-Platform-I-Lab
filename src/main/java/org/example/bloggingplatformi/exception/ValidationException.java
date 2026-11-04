package org.example.bloggingplatformi.exception;

/**
 * Thrown by a service when the user typed something wrong, for example an empty title.
 * The message is shown to the user as it is.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
