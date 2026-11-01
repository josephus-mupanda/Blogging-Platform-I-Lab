package org.example.bloggingplatformi.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorting and searching implemented directly, for Epic 3.2.
 *
 * <p>Java ships both of these, and production code should use what it ships.
 * These exist to be read and measured: Phase 4 benchmarks them against
 * {@code Collections.sort} and {@code Collections.binarySearch} and reports
 * the gap honestly rather than claiming a hand-written sort is faster.
 */
public final class Sorting {

    /**
     * Below this length the recursion and array copying cost more than the
     * quadratic comparisons they avoid. Real library sorts do the same thing;
     * {@code java.util.Arrays} switches to insertion sort at 32.
     */
    private static final int INSERTION_SORT_THRESHOLD = 12;

    private Sorting() {
    }

    // -----------------------------------------------------------------
    // Merge sort
    // -----------------------------------------------------------------

    /**
     * Sorts a copy of the list, leaving the input untouched.
     *
     * <p><b>Merge sort, not quicksort.</b> Merge sort is <em>stable</em> —
     * equal elements keep their original order — and that matters here: the
     * post list sorts by date, and two posts sharing a timestamp must not swap
     * places between one render and the next, or paging appears to duplicate
     * and drop rows. Quicksort is not stable and its worst case is O(n²) on
     * already-sorted input, which is exactly the shape data arrives in from an
     * ORDER BY.
     *
     * <p>O(n log n) in every case; O(n) extra space for the merge buffer.
     */
    public static <T> List<T> mergeSort(List<T> input, Comparator<? super T> comparator) {
        if (input == null || input.size() < 2) {
            return input == null ? new ArrayList<>() : new ArrayList<>(input);
        }
        List<T> working = new ArrayList<>(input);

        @SuppressWarnings("unchecked")
        T[] source = (T[]) working.toArray();
        @SuppressWarnings("unchecked")
        T[] buffer = (T[]) new Object[source.length];

        mergeSort(source, buffer, 0, source.length - 1, comparator);

        return new ArrayList<>(List.of(source));
    }

    private static <T> void mergeSort(T[] array, T[] buffer, int low, int high,
                                      Comparator<? super T> comparator) {
        if (high - low < INSERTION_SORT_THRESHOLD) {
            insertionSort(array, low, high, comparator);
            return;
        }
        // (low + high) >>> 1, not (low + high) / 2: the sum overflows to a
        // negative int for large arrays, which is the classic binary-search bug.
        int mid = (low + high) >>> 1;

        mergeSort(array, buffer, low, mid, comparator);
        mergeSort(array, buffer, mid + 1, high, comparator);

        // Already in order: the largest of the left half is no greater than
        // the smallest of the right. Skipping the merge makes sorted input
        // linear, which is the common case for data arriving from the database.
        if (comparator.compare(array[mid], array[mid + 1]) <= 0) {
            return;
        }
        merge(array, buffer, low, mid, high, comparator);
    }

    private static <T> void merge(T[] array, T[] buffer, int low, int mid, int high,
                                  Comparator<? super T> comparator) {
        System.arraycopy(array, low, buffer, low, high - low + 1);

        int left = low;
        int right = mid + 1;

        for (int i = low; i <= high; i++) {
            if (left > mid) {
                array[i] = buffer[right++];
            } else if (right > high) {
                array[i] = buffer[left++];
            } else if (comparator.compare(buffer[right], buffer[left]) < 0) {
                array[i] = buffer[right++];
            } else {
                // <= takes from the left, which is what preserves stability.
                array[i] = buffer[left++];
            }
        }
    }

    private static <T> void insertionSort(T[] array, int low, int high,
                                          Comparator<? super T> comparator) {
        for (int i = low + 1; i <= high; i++) {
            T key = array[i];
            int j = i - 1;
            while (j >= low && comparator.compare(array[j], key) > 0) {
                array[j + 1] = array[j];
                j--;
            }
            array[j + 1] = key;
        }
    }

    // -----------------------------------------------------------------
    // Binary search
    // -----------------------------------------------------------------

    /**
     * Finds {@code key} in a list already sorted by {@code comparator}.
     *
     * <p>O(log n), and <b>only correct on a sorted list</b> — on unsorted input
     * it does not fail, it quietly returns a wrong answer, which is why the
     * caller must guarantee the ordering.
     *
     * @return an index of a matching element, or {@code -(insertionPoint) - 1}
     *         when absent, matching {@link java.util.Collections#binarySearch}
     *         so the two are interchangeable in a benchmark
     */
    public static <T> int binarySearch(List<? extends T> sorted, T key,
                                       Comparator<? super T> comparator) {
        int low = 0;
        int high = sorted.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int comparison = comparator.compare(sorted.get(mid), key);

            if (comparison < 0) {
                low = mid + 1;
            } else if (comparison > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    /**
     * Index of the first element not ordered before {@code key}.
     *
     * <p>Where {@link #binarySearch} returns any match, this returns the
     * <em>first</em> one, which is what a range query needs: with duplicates,
     * "any match" is not enough to find where a run begins.
     */
    public static <T> int lowerBound(List<? extends T> sorted, T key,
                                     Comparator<? super T> comparator) {
        int low = 0;
        int high = sorted.size();

        while (low < high) {
            int mid = (low + high) >>> 1;
            if (comparator.compare(sorted.get(mid), key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Linear scan, kept for the benchmark.
     *
     * <p>It is the honest baseline: O(n) against binary search's O(log n), and
     * the comparison is only meaningful if both are measured on the same data.
     */
    public static <T> int linearSearch(List<? extends T> items, T key,
                                       Comparator<? super T> comparator) {
        for (int i = 0; i < items.size(); i++) {
            if (comparator.compare(items.get(i), key) == 0) {
                return i;
            }
        }
        return -1;
    }

    /** True if the list is ordered; used by tests to guard the binary searches. */
    public static <T> boolean isSorted(List<? extends T> items, Comparator<? super T> comparator) {
        for (int i = 1; i < items.size(); i++) {
            if (comparator.compare(items.get(i - 1), items.get(i)) > 0) {
                return false;
            }
        }
        return true;
    }
}
