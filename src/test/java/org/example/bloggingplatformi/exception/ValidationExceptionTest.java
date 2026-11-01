package org.example.bloggingplatformi.exception;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationExceptionTest {

    @Test
    @DisplayName("the collector gathers every problem rather than stopping at the first")
    void collectsAllErrors() {
        // This is the point of the type: the Phase 3 form marks all of its bad
        // inputs in one pass instead of making the user fix them one at a time.
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.reject("title", "A title is required.");
        errors.reject("body", "A post needs a body.");
        errors.reject("author", "A valid author is required.");

        ValidationException thrown =
                assertThrows(ValidationException.class, errors::throwIfInvalid);

        assertEquals(3, thrown.getErrors().size());
        assertTrue(thrown.getErrors().containsKey("title"));
        assertTrue(thrown.getErrors().containsKey("body"));
        assertTrue(thrown.getErrors().containsKey("author"));
    }

    @Test
    @DisplayName("field order is preserved so messages appear in form order")
    void preservesInsertionOrder() {
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.reject("username", "required");
        errors.reject("email", "required");
        errors.reject("password", "required");

        ValidationException thrown =
                assertThrows(ValidationException.class, errors::throwIfInvalid);

        assertEquals(List.of("username", "email", "password"),
                List.copyOf(thrown.getErrors().keySet()));
    }

    @Test
    @DisplayName("the first message for a field wins")
    void firstMessagePerFieldWins() {
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.reject("title", "A title is required.");
        errors.reject("title", "Titles must be at most 255 characters.");

        ValidationException thrown =
                assertThrows(ValidationException.class, errors::throwIfInvalid);

        assertEquals(1, thrown.getErrors().size());
        assertEquals("A title is required.", thrown.getErrors().get("title"));
    }

    @Test
    @DisplayName("rejectIf only records when the condition holds")
    void conditionalRejection() {
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.rejectIf(false, "title", "not recorded");
        errors.rejectIf(true, "body", "recorded");

        assertTrue(errors.hasErrors());
        ValidationException thrown =
                assertThrows(ValidationException.class, errors::throwIfInvalid);
        assertEquals(1, thrown.getErrors().size());
        assertTrue(thrown.getErrors().containsKey("body"));
    }

    @Test
    @DisplayName("a clean collector throws nothing")
    void noErrorsNoThrow() {
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.rejectIf(false, "title", "fine");

        assertFalse(errors.hasErrors());
        assertDoesNotThrow(errors::throwIfInvalid);
    }

    @Test
    @DisplayName("the message names every failing field")
    void messageListsFields() {
        ValidationException thrown = new ValidationException(
                new java.util.LinkedHashMap<>(java.util.Map.of("rating", "out of range")));

        assertTrue(thrown.getMessage().contains("rating"));
        assertTrue(thrown.getMessage().contains("out of range"));
    }

    @Test
    @DisplayName("the error map cannot be modified by a caller")
    void errorsAreImmutable() {
        ValidationException thrown = new ValidationException("title", "required");

        assertThrows(UnsupportedOperationException.class,
                () -> thrown.getErrors().put("body", "sneaky"));
    }

    @Test
    @DisplayName("a duplicate carries the field so the UI can highlight the right input")
    void duplicateCarriesField() {
        DuplicateEntityException thrown = new DuplicateEntityException("username", "ada_l");

        assertEquals("username", thrown.getField());
        assertTrue(thrown.getMessage().contains("ada_l"));
    }
}
