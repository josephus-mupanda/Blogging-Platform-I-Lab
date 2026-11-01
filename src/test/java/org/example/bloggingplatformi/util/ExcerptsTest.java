package org.example.bloggingplatformi.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcerptsTest {

    @Test
    @DisplayName("short bodies pass through unchanged")
    void shortBodyUnchanged() {
        assertEquals("A short post.", Excerpts.from("A short post."));
    }

    @Test
    @DisplayName("whitespace and newlines collapse to single spaces")
    void collapsesWhitespace() {
        assertEquals("One two three",
                Excerpts.from("  One\n\ttwo   three  "));
    }

    @Test
    @DisplayName("empty and null bodies produce an empty excerpt, never null")
    void handlesMissingBody() {
        // The column is nullable, but returning "" keeps the UI from having to
        // null-check a value it only ever renders.
        assertEquals("", Excerpts.from(null));
        assertEquals("", Excerpts.from("   "));
    }

    @Test
    @DisplayName("long bodies truncate on a word boundary with an ellipsis")
    void truncatesOnWordBoundary() {
        String body = "alpha bravo charlie delta echo foxtrot golf hotel india juliet";

        String excerpt = Excerpts.from(body, 30);

        assertTrue(excerpt.endsWith("..."), "expected an ellipsis, got: " + excerpt);
        assertTrue(excerpt.length() <= 30, "excerpt exceeded the limit: " + excerpt.length());
        // The cut must not land mid-word.
        assertFalse(excerpt.contains("charl..."), "cut mid-word: " + excerpt);
    }

    @Test
    @DisplayName("text with no nearby space still truncates rather than returning almost nothing")
    void truncatesUnbrokenText() {
        String body = "x".repeat(200);

        String excerpt = Excerpts.from(body, 50);

        assertEquals(50, excerpt.length());
        assertTrue(excerpt.endsWith("..."));
    }

    @Test
    @DisplayName("the excerpt never exceeds the VARCHAR(500) column")
    void respectsColumnWidth() {
        String body = "word ".repeat(1000);

        // Asking for more than the column allows must still produce something
        // the database will accept.
        String excerpt = Excerpts.from(body, 10_000);

        assertTrue(excerpt.length() <= Excerpts.MAX_LENGTH,
                "excerpt must fit posts.excerpt VARCHAR(500), was " + excerpt.length());
    }

    @Test
    @DisplayName("a body exactly at the limit is not truncated")
    void exactLengthNotTruncated() {
        String body = "a".repeat(100);

        assertEquals(body, Excerpts.from(body, 100));
    }
}
