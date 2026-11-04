package org.example.bloggingplatformi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Role;

/**
 * Tests that wrong input is refused with a clear message.
 * The checks happen before any database call, so no database is needed.
 */
class ValidationTest {

    private final PostService postService = new PostService();
    private final UserService userService = new UserService();

    @Test
    void aPostNeedsATitle() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> postService.create(1, "  ", "Some text", ""));
        assertEquals("A post needs a title.", error.getMessage());
    }

    @Test
    void aPostNeedsSomeText() {
        assertThrows(ValidationException.class, () -> postService.create(1, "A title", "", ""));
    }

    @Test
    void aShortPasswordIsRefused() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> userService.register("serge", "serge@mail.com", "123", "Serge", Role.AUTHOR));
        assertEquals("The password needs at least 6 characters.", error.getMessage());
    }

    @Test
    void nobodyCanSignUpAsAdmin() {
        assertThrows(ValidationException.class,
                () -> userService.register("boss", "boss@mail.com", "123456", "Boss", Role.ADMIN));
    }

    @Test
    void aWrongEmailIsRefused() {
        assertThrows(ValidationException.class,
                () -> userService.register("serge", "not-an-email", "123456", "Serge", Role.AUTHOR));
    }
}
