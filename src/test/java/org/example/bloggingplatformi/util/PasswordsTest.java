package org.example.bloggingplatformi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the password hashing. */
class PasswordsTest {

    @Test
    void theHashIsTheSha256OfThePassword() {
        // The same value as the admin password in db/postgres/seed.sql.
        assertEquals("bcb15f821479b4d5772bd0ca866c00ad5f926e3580720659cc80d39c9d09802a", Passwords.hash("111111"));
    }

    @Test
    void onlyTheRightPasswordMatches() {
        String stored = Passwords.hash("secret1");
        assertTrue(Passwords.matches("secret1", stored));
        assertFalse(Passwords.matches("secret2", stored));
    }
}
