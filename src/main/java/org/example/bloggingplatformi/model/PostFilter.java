package org.example.bloggingplatformi.model;

/**
 * The criteria behind the post list: search box, filters, sort and page.
 *
 * <p>Every field is optional. A filter with nothing set lists everything,
 * newest first. The DAO builds its WHERE clause from whichever fields are
 * populated and binds each value as a parameter.
 *
 * @param keyword    free text matched against title and body, case-insensitively
 * @param authorId   null for any author
 * @param tagId      null for any tag
 * @param status     null for any status
 * @param sort       never null; defaults to {@link PostSort#NEWEST}
 * @param pageNumber zero-based
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

    /** Guards against a client asking for the whole table in one page. */
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

    /** The reader-facing feed: published posts only. */
    public static PostFilter published() {
        return all().withStatus(PostStatus.PUBLISHED).withSort(PostSort.RECENTLY_PUBLISHED);
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    public long offset() {
        return (long) pageNumber * pageSize;
    }

    // Each change resets to page zero, except withPage itself. Changing a
    // filter while staying on page seven usually lands past the end of the
    // new result set, which reads as "the search found nothing".

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
