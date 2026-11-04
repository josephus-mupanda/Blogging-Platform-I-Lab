package org.example.bloggingplatformi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests for our merge sort and binary search. */
class SortingTest {

    @Test
    void mergeSortSortsTheList() {
        List<Integer> sorted = Sorting.mergeSort(List.of(5, 3, 9, 1, 3), Comparator.naturalOrder());
        assertEquals(List.of(1, 3, 3, 5, 9), sorted);
    }

    @Test
    void mergeSortWorksOnAnEmptyList() {
        assertEquals(List.of(), Sorting.mergeSort(List.<Integer>of(), Comparator.naturalOrder()));
    }

    @Test
    void binarySearchFindsTheIndex() {
        List<String> names = List.of("java", "mongodb", "sql");
        assertEquals(1, Sorting.binarySearch(names, "mongodb", Comparator.naturalOrder()));
    }

    @Test
    void binarySearchReturnsMinusOneWhenMissing() {
        List<String> names = List.of("java", "mongodb", "sql");
        assertEquals(-1, Sorting.binarySearch(names, "python", Comparator.naturalOrder()));
    }
}
