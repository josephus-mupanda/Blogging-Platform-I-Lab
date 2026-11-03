package org.example.bloggingplatformi.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Makes URL-safe slugs for posts and tags, like "my-first-post".
 * A slug is made once, when the post is created, and does not change if the title is edited.
 */
public final class Slugs {

    /** Same as the VARCHAR(280) size of posts.slug. */
    public static final int MAX_POST_SLUG_LENGTH = 280;

    /** Same as the VARCHAR(60) size of tags.slug. */
    public static final int MAX_TAG_SLUG_LENGTH = 60;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private Slugs() {
    }

    /**
     * Turns text into a lower-case slug with hyphens between words.
     * Accents are removed but the letter is kept, so "Café Patterns" becomes "cafe-patterns".
     * If nothing is left, for example with non-Latin text, the fallback is used.
     */
    public static String slugify(String input, int maxLength) {
        return slugify(input, maxLength, "post");
    }

    public static String slugify(String input, int maxLength, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }

        // NFD splits "é" into "e" plus an accent mark, so we can remove just the mark.
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");

        String slug = NON_ALPHANUMERIC
                .matcher(normalized.toLowerCase(Locale.ROOT))
                .replaceAll("-");
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");

        if (slug.isEmpty()) {
            return fallback;
        }
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength);
            // Cutting the slug short can leave a hyphen at the end.
            slug = EDGE_HYPHENS.matcher(slug).replaceAll("");
        }
        return slug;
    }

    public static String forPost(String title) {
        return slugify(title, MAX_POST_SLUG_LENGTH, "post");
    }

    public static String forTag(String name) {
        return slugify(name, MAX_TAG_SLUG_LENGTH, "tag");
    }

    /**
     * Adds -2, -3 and so on until the slug is free. Another user could still take
     * the same slug before the insert, so the database unique constraint is the final check.
     *
     * @param isTaken returns true when the slug already exists
     */
    public static String uniquify(String base, int maxLength, Predicate<String> isTaken) {
        if (!isTaken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String tail = "-" + suffix;
            String head = base.length() + tail.length() > maxLength
                    ? base.substring(0, maxLength - tail.length())
                    : base;
            String candidate = EDGE_HYPHENS.matcher(head).replaceAll("") + tail;
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        // After 1000 tries something is wrong, so use the current time to make it unique.
        return base + "-" + System.currentTimeMillis();
    }
}
