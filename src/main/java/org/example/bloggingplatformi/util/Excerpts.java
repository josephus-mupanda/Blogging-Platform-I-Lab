package org.example.bloggingplatformi.util;

/**
 * Builds the short excerpt saved with each post. The post list shows the
 * excerpt, so it does not need to load the full body.
 */
public final class Excerpts {

    /** Same as the VARCHAR(500) size of posts.excerpt. */
    public static final int MAX_LENGTH = 500;

    /** A good length for one row of the post list. */
    public static final int DEFAULT_LENGTH = 300;

    private static final String ELLIPSIS = "...";

    private Excerpts() {
    }

    public static String from(String body) {
        return from(body, DEFAULT_LENGTH);
    }

    /**
     * Joins all whitespace into single spaces and cuts the text at the end of a word.
     * If there is no space near the limit, for example in a long URL, it cuts mid-word.
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

        // Only go back to the last space if that keeps at least 60% of the text.
        if (lastSpace > cut * 0.6) {
            head = head.substring(0, lastSpace);
        }
        return head.stripTrailing() + ELLIPSIS;
    }
}
