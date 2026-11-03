package org.example.bloggingplatformi.model;

/**
 * The sort options on the post list. Each option has a fixed piece of SQL.
 * ORDER BY cannot use a ? parameter, so using fixed text here prevents SQL injection.
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
     * The text after ORDER BY. Each option ends with post_id to break ties.
     * Without it, two posts with the same time could swap between pages,
     * so one is skipped and the other is shown twice.
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
