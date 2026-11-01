package org.example.bloggingplatformi.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Generates URL-safe slugs for posts and tags.
 *
 * <p>A slug is a candidate key, not a copy of the title: once assigned it does
 * not change when the title is edited, so links keep working. Generation
 * therefore happens exactly once, at creation.
 */
public final class Slugs {

    /** Matches the {@code VARCHAR(280)} width of {@code posts.slug}. */
    public static final int MAX_POST_SLUG_LENGTH = 280;

    /** Matches the {@code VARCHAR(60)} width of {@code tags.slug}. */
    public static final int MAX_TAG_SLUG_LENGTH = 60;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private Slugs() {
    }

    /**
     * Converts arbitrary text into a lower-case, hyphen-separated slug.
     *
     * <p>Accents are folded rather than dropped, so "Café Patterns" becomes
     * {@code cafe-patterns} and not {@code caf-patterns}. Text in a script
     * with no Latin equivalent folds away entirely, which is why
     * {@link #slugify(String, int, String)} takes a fallback.
     */
    public static String slugify(String input, int maxLength) {
        return slugify(input, maxLength, "post");
    }

    public static String slugify(String input, int maxLength, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }

        // NFD splits "é" into "e" + combining acute, so the mark can be removed
        // on its own and the base letter survives.
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
            // Truncating mid-word can leave a trailing hyphen.
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
     * Appends {@code -2}, {@code -3} and so on until {@code isTaken} says the
     * slug is free.
     *
     * <p>This is a convenience, not a guarantee. Between the check and the
     * insert another transaction can claim the same slug; the
     * {@code uq_posts_slug} constraint is what actually prevents the duplicate,
     * and the DAO turns that violation into a
     * {@link org.example.bloggingplatformi.exception.DuplicateEntityException}.
     *
     * @param isTaken returns true when the candidate already exists
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
        // A thousand collisions means something is wrong with the caller, not
        // with the data. Fall back to something guaranteed unique.
        return base + "-" + System.currentTimeMillis();
    }
}
