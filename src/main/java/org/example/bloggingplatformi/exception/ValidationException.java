package org.example.bloggingplatformi.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds all the input errors found by a service, one message per field.
 * Sending them together lets the form mark every bad field at once.
 */
public class ValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(buildMessage(errors));
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public ValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    /** Maps each field name to its error message, in the order they were added. */
    public Map<String, String> getErrors() {
        return errors;
    }

    private static String buildMessage(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Validation failed";
        }
        StringBuilder sb = new StringBuilder("Validation failed: ");
        errors.forEach((field, message) -> sb.append(field).append(" - ").append(message).append("; "));
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    /** Collects errors, then throws one ValidationException if there are any. */
    public static final class Collector {
        private final Map<String, String> errors = new LinkedHashMap<>();

        /** Adds an error for a field. If the field already has one, the first is kept. */
        public Collector reject(String field, String message) {
            errors.putIfAbsent(field, message);
            return this;
        }

        public Collector rejectIf(boolean condition, String field, String message) {
            if (condition) {
                reject(field, message);
            }
            return this;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public void throwIfInvalid() {
            if (hasErrors()) {
                throw new ValidationException(errors);
            }
        }
    }
}
