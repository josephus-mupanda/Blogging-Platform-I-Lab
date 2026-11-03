package org.example.bloggingplatformi.model;

/**
 * What the post list should show: search text, filters, sort order and page.
 * Every field is optional. An empty filter lists all posts, newest first.
 *
 * @param keyword    text to find in the title or body, ignoring case
 * @param authorId   null for any author
 * @param tagId      null for any tag
 * @param status     null for any status
 * @param sort       never null, defaults to NEWEST
 * @param pageNumber starts at 0
 */
public record PostFilter(
        String keyword,
        Long authorId,
        Integer tagId,
        PostStatus status,
        PostSort sort,
        int pageNumber,
        int pageSize
) {

    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Stops a caller from asking for the whole table in one page. */
    public static final int MAX_PAGE_SIZE = 200;

    public PostFilter {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.strip();
        sort = (sort == null) ? PostSort.NEWEST : sort;
        pageNumber = Math.max(0, pageNumber);
        pageSize = (pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public static PostFilter all() {
        return new PostFilter(null, null, null, null, PostSort.NEWEST, 0, DEFAULT_PAGE_SIZE);
    }

    /** Published posts only, most recently published first. */
    public static PostFilter published() {
        return all().withStatus(PostStatus.PUBLISHED).withSort(PostSort.RECENTLY_PUBLISHED);
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    public long offset() {
        return (long) pageNumber * pageSize;
    }

    // Each "with" method goes back to page 0, except withPage. Otherwise a new
    // filter could leave you on a page past the end, which looks like no results.

    public PostFilter withKeyword(String value) {
        return new PostFilter(value, authorId, tagId, status, sort, 0, pageSize);
    }

    public PostFilter withAuthorId(Long value) {
        return new PostFilter(keyword, value, tagId, status, sort, 0, pageSize);
    }

    public PostFilter withTagId(Integer value) {
        return new PostFilter(keyword, authorId, value, status, sort, 0, pageSize);
    }

    public PostFilter withStatus(PostStatus value) {
        return new PostFilter(keyword, authorId, tagId, value, sort, 0, pageSize);
    }

    public PostFilter withSort(PostSort value) {
        return new PostFilter(keyword, authorId, tagId, status, value, 0, pageSize);
    }

    public PostFilter withPage(int value) {
        return new PostFilter(keyword, authorId, tagId, status, sort, value, pageSize);
    }

    public PostFilter withPageSize(int value) {
        return new PostFilter(keyword, authorId, tagId, status, sort, 0, value);
    }

    public PostFilter nextPage() {
        return withPage(pageNumber + 1);
    }

    public PostFilter previousPage() {
        return withPage(Math.max(0, pageNumber - 1));
    }
}
