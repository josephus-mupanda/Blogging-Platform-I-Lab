package org.example.bloggingplatformi.model;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results plus the totals the UI needs to render pager controls.
 *
 * @param content    the rows on this page
 * @param pageNumber zero-based
 * @param pageSize   rows requested per page
 * @param totalElements total rows matching the query, ignoring pagination
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
        // Integer ceiling division; avoids floating point entirely.
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

    /** Zero-based index of the first row on this page, for "showing 21-40 of 97". */
    public long firstElementIndex() {
        return (long) pageNumber * pageSize;
    }

    /** Converts the rows while keeping the paging metadata intact. */
    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(content.stream().<R>map(mapper).toList(),
                pageNumber, pageSize, totalElements);
    }
}
