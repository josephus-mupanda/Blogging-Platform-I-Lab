package org.example.bloggingplatformi.model;

/**
 * The sort options offered on the post list.
 *
 * <p>The SQL fragment is a fixed constant per enum value, never built from
 * user input. That is what makes it safe to concatenate into the ORDER BY
 * clause: a bind parameter cannot appear there, so the alternative to a
 * closed set of constants would be string-building from request data, which
 * is exactly the injection vector this enum removes.
 */
public enum PostSort {

    NEWEST("p.created_at DESC, p.post_id DESC", "Newest first"),
    OLDEST("p.created_at ASC, p.post_id ASC", "Oldest first"),
    RECENTLY_PUBLISHED("p.published_at DESC NULLS LAST, p.post_id DESC", "Recently published"),
    MOST_VIEWED("p.view_count DESC, p.post_id DESC", "Most viewed"),
    TITLE_ASC("LOWER(p.title) ASC, p.post_id ASC", "Title A-Z"),
    TITLE_DESC("LOWER(p.title) DESC, p.post_id DESC", "Title Z-A");

    private final String sqlFragment;
    private final String label;

    PostSort(String sqlFragment, String label) {
        this.sqlFragment = sqlFragment;
        this.label = label;
    }

    /**
     * The ORDER BY body. Every option ends with a tie-break on {@code post_id}
     * because a non-deterministic sort makes paging drop and repeat rows: two
     * posts sharing a timestamp can swap places between page one and page two.
     */
    public String sqlFragment() {
        return sqlFragment;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
