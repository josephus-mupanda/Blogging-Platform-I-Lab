package org.example.bloggingplatformi.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for Passwords, which hashes and checks the passwords used to sign in. */
class PasswordsTest {

    @Test
    @DisplayName("the right password matches its hash, a wrong one does not")
    void matchesOnlyTheRightPassword() {
        String stored = Passwords.hash("admin123");

        assertTrue(Passwords.matches("admin123", stored));
        assertFalse(Passwords.matches("admin124", stored));
        assertFalse(Passwords.matches("", stored));
    }

    @Test
    @DisplayName("the plain password is not stored, and the salt makes each hash different")
    void hashIsSaltedAndHidesThePassword() {
        String first = Passwords.hash("secret99");
        String second = Passwords.hash("secret99");

        assertFalse(first.contains("secret99"));
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("a broken stored value never matches")
    void brokenHashNeverMatches() {
        assertFalse(Passwords.matches("admin123", null));
        assertFalse(Passwords.matches("admin123", "no-colon-here"));
        assertFalse(Passwords.matches("admin123", "!!!:???"));
    }
}
