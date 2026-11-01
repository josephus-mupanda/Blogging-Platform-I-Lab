package org.example.bloggingplatformi.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries field-level validation failures from the service layer to the UI.
 *
 * <p>Collecting every problem into one exception, rather than throwing on the
 * first, is what lets the Phase 3 form mark all of its bad inputs in a single
 * pass instead of making the user fix them one at a time.
 *
 * <p>Insertion order is preserved so the messages appear in field order.
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

    /** Field name to human-readable message. Never empty. */
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

    /**
     * Accumulates problems, then throws only if any were found. Keeps the
     * validate-then-throw pattern in the services down to a few lines.
     */
    public static final class Collector {
        private final Map<String, String> errors = new LinkedHashMap<>();

        /** Records a problem against a field. The first message for a field wins. */
        public Collector reject(String field, String message) {
            errors.putIfAbsent(field, message);
            return this;
        }

        /** Records the problem only when {@code condition} holds. */
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
