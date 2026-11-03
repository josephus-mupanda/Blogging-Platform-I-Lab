package org.example.bloggingplatformi.model;

import java.util.List;

/**
 * One page of results, plus the totals the UI needs for its page buttons.
 *
 * @param content       the rows on this page
 * @param pageNumber    starts at 0
 * @param pageSize      rows asked for per page
 * @param totalElements rows that match the query across all pages
 */
public record Page<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements
) {

    public Page {
        content = List.copyOf(content);
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
    }

    public static <T> Page<T> empty(int pageNumber, int pageSize) {
        return new Page<>(List.of(), pageNumber, pageSize, 0);
    }

    public int totalPages() {
        if (totalElements == 0) {
            return 0;
        }
        // Rounds up using whole numbers only, so 41 rows at 20 per page gives 3 pages.
        return (int) ((totalElements + pageSize - 1) / pageSize);
    }

    public boolean hasNext() {
        return pageNumber + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return pageNumber > 0;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    /** Index of the first row on this page, starting at 0. Used for "showing 21-40 of 97". */
    public long firstElementIndex() {
        return (long) pageNumber * pageSize;
    }
}
