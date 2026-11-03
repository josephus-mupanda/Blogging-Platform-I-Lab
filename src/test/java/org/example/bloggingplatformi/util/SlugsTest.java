package org.example.bloggingplatformi.util;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for Slugs, which turns titles into URL-safe slugs and keeps them unique. */
class SlugsTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            "'Designing a Blogging Schema',  designing-a-blogging-schema",
            "'B-tree or GIN?',               b-tree-or-gin",
            "'  Leading and trailing  ',     leading-and-trailing",
            "'Multiple   inner   spaces',    multiple-inner-spaces",
            "'Symbols!@#$%^&*()here',        symbols-here",
            "'ALL CAPS TITLE',               all-caps-title",
            "'snake_case_input',             snake-case-input",
            "'2026 Performance Report',      2026-performance-report"
    })
    @DisplayName("slugify lower-cases, collapses separators and trims the edges")
    void slugifiesText(String input, String expected) {
        assertEquals(expected, Slugs.forPost(input));
    }

    @Test
    @DisplayName("accents fold to their base letter rather than being dropped")
    void foldsAccents() {
        // NFD splits "é" into "e" plus an accent mark. The mark is removed and "e" stays.
        assertEquals("cafe-patterns", Slugs.forPost("Café Patterns"));
        assertEquals("uber-naive-resume", Slugs.forPost("Über naïve résumé"));
    }

    @Test
    @DisplayName("text with no Latin equivalent falls back instead of returning empty")
    void fallsBackWhenNothingSurvives() {
        // Slugs must be unique, so an empty slug would clash with other titles like this one.
        assertEquals("post", Slugs.forPost("..."));
        assertEquals("post", Slugs.forPost("   "));
        assertEquals("post", Slugs.forPost(null));
        assertEquals("tag", Slugs.forTag("!!!"));
    }

    @Test
    @DisplayName("truncation respects the column width and leaves no trailing hyphen")
    void truncatesToColumnWidth() {
        String longTitle = "word ".repeat(200);
        String slug = Slugs.forPost(longTitle);

        assertTrue(slug.length() <= Slugs.MAX_POST_SLUG_LENGTH,
                "slug must fit posts.slug VARCHAR(280), was " + slug.length());
        assertFalse(slug.endsWith("-"), "truncation left a trailing hyphen: " + slug);
    }

    @Test
    @DisplayName("tag slugs honour the narrower VARCHAR(60) column")
    void tagSlugsUseTheTagWidth() {
        String slug = Slugs.forTag("a".repeat(100));
        assertTrue(slug.length() <= Slugs.MAX_TAG_SLUG_LENGTH,
                "tag slug must fit VARCHAR(60), was " + slug.length());
    }

    @Test
    @DisplayName("uniquify returns the base when it is free")
    void keepsBaseWhenFree() {
        assertEquals("my-post",
                Slugs.uniquify("my-post", Slugs.MAX_POST_SLUG_LENGTH, taken -> false));
    }

    @Test
    @DisplayName("uniquify appends an increasing suffix until one is free")
    void appendsSuffixOnCollision() {
        Set<String> existing = Set.of("my-post", "my-post-2", "my-post-3");

        assertEquals("my-post-4",
                Slugs.uniquify("my-post", Slugs.MAX_POST_SLUG_LENGTH, existing::contains));
    }

    @Test
    @DisplayName("a suffixed slug still fits the column")
    void suffixDoesNotOverflowTheColumn() {
        String base = "a".repeat(Slugs.MAX_POST_SLUG_LENGTH);

        // The base is already at the limit, so the start must be cut to make room for the suffix.
        String slug = Slugs.uniquify(base, Slugs.MAX_POST_SLUG_LENGTH,
                candidate -> candidate.equals(base) || candidate.endsWith("-2"));

        assertTrue(slug.length() <= Slugs.MAX_POST_SLUG_LENGTH,
                "suffixed slug overflowed the column: " + slug.length());
    }
}
