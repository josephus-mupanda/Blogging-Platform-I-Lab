package org.example.bloggingplatformi.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Our own sorting and searching algorithms (the DSA part of the lab). */
public final class Sorting {

    private Sorting() {
    }

    /**
     * Merge sort: split the list in two halves, sort each half, then merge them.
     * Always O(n log n). Returns a new sorted list; the input is not changed.
     * Used by TagService to sort the tags by name.
     */
    public static <T> List<T> mergeSort(List<T> list, Comparator<T> comparator) {
        if (list.size() <= 1) {
            return new ArrayList<>(list); // 0 or 1 item: already sorted
        }
        int middle = list.size() / 2;
        List<T> left = mergeSort(list.subList(0, middle), comparator);
        List<T> right = mergeSort(list.subList(middle, list.size()), comparator);
        return merge(left, right, comparator);
    }

    /** Takes the smaller first item of the two sorted lists, again and again. */
    private static <T> List<T> merge(List<T> left, List<T> right, Comparator<T> comparator) {
        List<T> result = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            if (comparator.compare(left.get(i), right.get(j)) <= 0) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        while (i < left.size()) {
            result.add(left.get(i++));
        }
        while (j < right.size()) {
            result.add(right.get(j++));
        }
        return result;
    }

    /**
     * Binary search: look at the middle item and keep the half where the key must be.
     * O(log n), but the list must already be sorted.
     * Returns the index of the key, or -1 if it is not in the list.
     * Used by TagService to check if a tag name already exists.
     */
    public static <T> int binarySearch(List<T> sorted, T key, Comparator<T> comparator) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low <= high) {
            int middle = (low + high) / 2;
            int result = comparator.compare(sorted.get(middle), key);
            if (result == 0) {
                return middle;
            } else if (result < 0) {
                low = middle + 1;  // the key is in the right half
            } else {
                high = middle - 1; // the key is in the left half
            }
        }
        return -1;
    }

    /** Linear search: check every item. O(n). Only used by the benchmark, to compare. */
    public static <T> int linearSearch(List<T> list, T key, Comparator<T> comparator) {
        for (int i = 0; i < list.size(); i++) {
            if (comparator.compare(list.get(i), key) == 0) {
                return i;
            }
        }
        return -1;
    }
}
