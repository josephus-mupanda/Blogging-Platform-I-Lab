package org.example.bloggingplatformi.model;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationTest {

    @Test
    @DisplayName("total pages rounds up, so a partial last page still counts")
    void totalPagesRoundsUp() {
        assertEquals(5, new Page<>(List.of(), 0, 20, 97).totalPages());
        assertEquals(5, new Page<>(List.of(), 0, 20, 100).totalPages());
        assertEquals(6, new Page<>(List.of(), 0, 20, 101).totalPages());
    }

    @Test
    @DisplayName("an empty result set has zero pages, not one")
    void emptyHasNoPages() {
        assertEquals(0, Page.empty(0, 20).totalPages());
        assertFalse(Page.empty(0, 20).hasNext());
        assertTrue(Page.empty(0, 20).isEmpty());
    }

    @Test
    @DisplayName("navigation flags reflect position in the result set")
    void navigationFlags() {
        Page<String> first = new Page<>(List.of("a"), 0, 20, 97);
        Page<String> middle = new Page<>(List.of("a"), 2, 20, 97);
        Page<String> last = new Page<>(List.of("a"), 4, 20, 97);

        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());

        assertTrue(middle.hasPrevious());
        assertTrue(middle.hasNext());

        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext(), "the last page must not offer a next page");
    }

    @Test
    @DisplayName("the first element index drives the 'showing X to Y' label")
    void firstElementIndex() {
        assertEquals(0, new Page<>(List.of(), 0, 20, 97).firstElementIndex());
        assertEquals(40, new Page<>(List.of(), 2, 20, 97).firstElementIndex());
    }

    @Test
    @DisplayName("mapping preserves the paging metadata")
    void mapKeepsMetadata() {
        Page<Integer> source = new Page<>(List.of(1, 2, 3), 1, 20, 97);

        Page<String> mapped = source.map(String::valueOf);

        assertEquals(List.of("1", "2", "3"), mapped.content());
        assertEquals(1, mapped.pageNumber());
        assertEquals(97, mapped.totalElements());
        assertEquals(5, mapped.totalPages());
    }

    @Test
    @DisplayName("PostFilter clamps the page size so one request cannot pull the whole table")
    void clampsPageSize() {
        assertEquals(PostFilter.MAX_PAGE_SIZE,
                PostFilter.all().withPageSize(100_000).pageSize());
        assertEquals(PostFilter.DEFAULT_PAGE_SIZE,
                PostFilter.all().withPageSize(0).pageSize());
    }

    @Test
    @DisplayName("PostFilter never produces a negative page number")
    void clampsPageNumber() {
        assertEquals(0, PostFilter.all().withPage(-5).pageNumber());
        assertEquals(0, PostFilter.all().previousPage().pageNumber());
    }

    @Test
    @DisplayName("changing a filter resets to page zero")
    void changingFilterResetsPage() {
        // Keeping the page number while changing the filter usually lands past
        // the end of the new result set, which reads to the user as "no results".
        PostFilter deep = PostFilter.all().withPage(7);

        assertEquals(0, deep.withKeyword("index").pageNumber());
        assertEquals(0, deep.withStatus(PostStatus.PUBLISHED).pageNumber());
        assertEquals(0, deep.withSort(PostSort.MOST_VIEWED).pageNumber());
        assertEquals(0, deep.withTagId(3).pageNumber());
    }

    @Test
    @DisplayName("paging forward keeps the filter and advances the offset")
    void nextPageKeepsFilter() {
        PostFilter filter = PostFilter.all().withKeyword("caching").nextPage();

        assertEquals("caching", filter.keyword());
        assertEquals(1, filter.pageNumber());
        assertEquals(PostFilter.DEFAULT_PAGE_SIZE, filter.offset());
    }

    @Test
    @DisplayName("a blank keyword is treated as absent")
    void blankKeywordIsNull() {
        assertFalse(PostFilter.all().withKeyword("   ").hasKeyword());
        assertTrue(PostFilter.all().withKeyword(" indexing ").hasKeyword());
        assertEquals("indexing", PostFilter.all().withKeyword(" indexing ").keyword());
    }

    @Test
    @DisplayName("every sort option tie-breaks on post_id")
    void everySortIsDeterministic() {
        // Without a unique tie-break, two rows sharing a timestamp can swap
        // between page one and page two, so paging drops and repeats rows.
        for (PostSort sort : PostSort.values()) {
            assertTrue(sort.sqlFragment().contains("post_id"),
                    sort + " has no tie-break column, so paging is not stable");
        }
    }
}
