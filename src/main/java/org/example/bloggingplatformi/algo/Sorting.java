package org.example.bloggingplatformi.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hand-written merge sort and binary search.
 * The benchmark compares them with Collections.sort and Collections.binarySearch.
 */
public final class Sorting {

    private Sorting() {
    }

    // -----------------------------------------------------------------
    // Merge sort
    // -----------------------------------------------------------------

    /**
     * Merge sort: split the list in two, sort each half, then merge them.
     * O(n log n) time, O(n) extra space. Returns a new sorted list.
     *
     * Merge sort is stable: equal items keep their order. So two posts with the
     * same date do not swap places between page loads.
     */
    public static <T> List<T> mergeSort(List<T> input, Comparator<T> comparator) {
        if (input == null) {
            return new ArrayList<>();
        }
        if (input.size() <= 1) {
            return new ArrayList<>(input);
        }
        int middle = input.size() / 2;
        List<T> left = mergeSort(input.subList(0, middle), comparator);
        List<T> right = mergeSort(input.subList(middle, input.size()), comparator);
        return merge(left, right, comparator);
    }

    /** Merges two sorted lists into one sorted list. */
    private static <T> List<T> merge(List<T> left, List<T> right, Comparator<T> comparator) {
        List<T> result = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;

        while (i < left.size() && j < right.size()) {
            // On a tie we take from the left list. This keeps the sort stable.
            if (comparator.compare(left.get(i), right.get(j)) <= 0) {
                result.add(left.get(i));
                i++;
            } else {
                result.add(right.get(j));
                j++;
            }
        }
        // One list is empty now. Copy what is left of the other one.
        while (i < left.size()) {
            result.add(left.get(i));
            i++;
        }
        while (j < right.size()) {
            result.add(right.get(j));
            j++;
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Binary search
    // -----------------------------------------------------------------

    /**
     * Binary search: look at the middle item, then keep only the half that can
     * hold the key. O(log n). The list must already be sorted.
     *
     * @return the index of a match, or -(insertionPoint) - 1 if not found,
     *         the same as Collections.binarySearch
     */
    public static <T> int binarySearch(List<T> sorted, T key, Comparator<T> comparator) {
        int low = 0;
        int high = sorted.size() - 1;

        while (low <= high) {
            int mid = (low + high) / 2;
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
     * Binary search for the first index whose item is not less than the key.
     * Unlike binarySearch, this finds the first of several equal items. O(log n).
     */
    public static <T> int lowerBound(List<T> sorted, T key, Comparator<T> comparator) {
        int low = 0;
        int high = sorted.size();

        while (low < high) {
            int mid = (low + high) / 2;
            if (comparator.compare(sorted.get(mid), key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** Checks every item in turn. O(n). Used in the benchmark to compare with binary search. */
    public static <T> int linearSearch(List<T> items, T key, Comparator<T> comparator) {
        for (int i = 0; i < items.size(); i++) {
            if (comparator.compare(items.get(i), key) == 0) {
                return i;
            }
        }
        return -1;
    }

    /** True if the list is sorted. Used by tests before a binary search. */
    public static <T> boolean isSorted(List<T> items, Comparator<T> comparator) {
        for (int i = 1; i < items.size(); i++) {
            if (comparator.compare(items.get(i - 1), items.get(i)) > 0) {
                return false;
            }
        }
        return true;
    }
}
