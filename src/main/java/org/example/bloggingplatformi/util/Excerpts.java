package org.example.bloggingplatformi.util;

/**
 * Builds the precomputed {@code posts.excerpt} value.
 *
 * <p>The excerpt is a deliberate denormalization (design document, section 4):
 * it exists so the list screen never pulls full post bodies over the wire. It
 * is regenerated on every body change, which is what keeps it from drifting.
 */
public final class Excerpts {

    /** Matches the {@code VARCHAR(500)} width of {@code posts.excerpt}. */
    public static final int MAX_LENGTH = 500;

    /** Comfortable length for a list row; well under the column width. */
    public static final int DEFAULT_LENGTH = 300;

    private static final String ELLIPSIS = "...";

    private Excerpts() {
    }

    public static String from(String body) {
        return from(body, DEFAULT_LENGTH);
    }

    /**
     * Collapses whitespace and truncates on a word boundary.
     *
     * <p>Cutting mid-word reads as a typo rather than as a summary, so the cut
     * backs up to the last space when one is reasonably close. If the text has
     * no space near the limit (a long URL, say) it cuts hard rather than
     * returning almost nothing.
     */
    public static String from(String body, int maxLength) {
        if (body == null || body.isBlank()) {
            return "";
        }
        int limit = Math.min(maxLength, MAX_LENGTH);

        String flattened = body.strip().replaceAll("\\s+", " ");
        if (flattened.length() <= limit) {
            return flattened;
        }

        int cut = limit - ELLIPSIS.length();
        String head = flattened.substring(0, cut);
        int lastSpace = head.lastIndexOf(' ');

        // Only honour the word boundary if it is not throwing away most of the
        // excerpt; 60% of the budget is the cutoff.
        if (lastSpace > cut * 0.6) {
            head = head.substring(0, lastSpace);
        }
        return head.stripTrailing() + ELLIPSIS;
    }
}
