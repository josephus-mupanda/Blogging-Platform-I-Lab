package org.example.bloggingplatformi.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Correctness tests for the Phase 4 data structures and algorithms. */
class AlgorithmsTest {

    @Nested
    @DisplayName("Merge sort")
    class MergeSortTests {

        @Test
        @DisplayName("sorts integers")
        void sortsIntegers() {
            List<Integer> input = List.of(5, 3, 9, 1, 7, 2, 8, 6, 4);

            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9),
                    Sorting.mergeSort(input, Comparator.naturalOrder()));
        }

        @Test
        @DisplayName("leaves the input list untouched")
        void doesNotMutateInput() {
            List<Integer> input = new ArrayList<>(List.of(3, 1, 2));

            Sorting.mergeSort(input, Comparator.naturalOrder());

            assertEquals(List.of(3, 1, 2), input);
        }

        @Test
        @DisplayName("handles empty, single and null input")
        void handlesDegenerateInput() {
            // Explicit type witnesses: with an empty or null list there is no
            // element for the compiler to infer T from.
            assertEquals(List.of(),
                    Sorting.<Integer>mergeSort(List.of(), Comparator.naturalOrder()));
            assertEquals(List.of(1),
                    Sorting.mergeSort(List.of(1), Comparator.<Integer>naturalOrder()));
            assertEquals(List.of(),
                    Sorting.<Integer>mergeSort(null, Comparator.naturalOrder()));
        }

        @Test
        @DisplayName("is stable: equal elements keep their original order")
        void isStable() {
            // Stability is why merge sort was chosen. Two posts sharing a
            // timestamp must not swap places between renders, or paging
            // appears to duplicate and drop rows.
            record Entry(String key, int order) { }

            List<Entry> input = List.of(
                    new Entry("b", 1), new Entry("a", 2), new Entry("b", 3),
                    new Entry("a", 4), new Entry("b", 5), new Entry("a", 6),
                    new Entry("c", 7), new Entry("a", 8), new Entry("b", 9),
                    new Entry("c", 10), new Entry("a", 11), new Entry("b", 12),
                    new Entry("c", 13), new Entry("a", 14), new Entry("b", 15));

            List<Entry> sorted = Sorting.mergeSort(input, Comparator.comparing(Entry::key));

            List<Integer> aOrders = sorted.stream()
                    .filter(e -> e.key().equals("a"))
                    .map(Entry::order)
                    .toList();

            assertEquals(List.of(2, 4, 6, 8, 11, 14), aOrders,
                    "equal keys must retain their input order");
        }

        @Test
        @DisplayName("crosses the insertion-sort threshold correctly")
        void correctAroundThreshold() {
            // The small-array branch is a separate code path; sizes either
            // side of the cutover have to agree with the library sort.
            for (int size : new int[]{1, 11, 12, 13, 24, 100}) {
                List<Integer> input = descending(size);

                List<Integer> expected = new ArrayList<>(input);
                Collections.sort(expected);

                assertEquals(expected, Sorting.mergeSort(input, Comparator.naturalOrder()),
                        "mismatch at size " + size);
            }
        }

        @Test
        @DisplayName("agrees with Collections.sort on random data")
        void agreesWithLibrarySort() {
            Random random = new Random(42);

            for (int trial = 0; trial < 60; trial++) {
                List<Integer> input = new ArrayList<>();
                for (int i = 0; i < random.nextInt(400); i++) {
                    input.add(random.nextInt(100));
                }

                List<Integer> expected = new ArrayList<>(input);
                Collections.sort(expected);

                assertEquals(expected, Sorting.mergeSort(input, Comparator.naturalOrder()),
                        "mismatch on trial " + trial);
            }
        }

        @Test
        @DisplayName("handles already-sorted and reversed input")
        void handlesPreSortedInput() {
            // descending(200) counts down from 200 to 1, so its sorted form is
            // 1..200 — built here rather than assumed, which is what the first
            // version of this test got wrong.
            List<Integer> reversed = descending(200);
            List<Integer> expected = new ArrayList<>(reversed);
            Collections.sort(expected);

            assertEquals(expected, Sorting.mergeSort(expected, Comparator.naturalOrder()),
                    "already-sorted input must come back unchanged");
            assertEquals(expected, Sorting.mergeSort(reversed, Comparator.naturalOrder()),
                    "fully reversed input is merge sort's other boundary case");
        }

        @Test
        @DisplayName("sorts by a descending comparator")
        void sortsDescending() {
            assertEquals(List.of(5, 4, 3, 2, 1),
                    Sorting.mergeSort(List.of(3, 1, 5, 2, 4), Comparator.reverseOrder()));
        }

        private List<Integer> descending(int size) {
            List<Integer> values = new ArrayList<>(size);
            for (int i = size; i > 0; i--) {
                values.add(i);
            }
            return values;
        }
    }

    @Nested
    @DisplayName("Binary search")
    class BinarySearchTests {

        private final List<Integer> sorted = List.of(1, 3, 5, 7, 9, 11, 13, 15);

        @Test
        @DisplayName("finds every present element")
        void findsPresentElements() {
            for (int i = 0; i < sorted.size(); i++) {
                assertEquals(i, Sorting.binarySearch(sorted, sorted.get(i),
                        Comparator.<Integer>naturalOrder()));
            }
        }

        @Test
        @DisplayName("returns the insertion point for an absent element")
        void returnsInsertionPoint() {
            // Matching Collections.binarySearch exactly, so the two are
            // interchangeable in a benchmark.
            assertEquals(-(0) - 1, Sorting.binarySearch(sorted, 0, Comparator.<Integer>naturalOrder()));
            assertEquals(-(1) - 1, Sorting.binarySearch(sorted, 2, Comparator.<Integer>naturalOrder()));
            assertEquals(-(8) - 1, Sorting.binarySearch(sorted, 99, Comparator.<Integer>naturalOrder()));
        }

        @Test
        @DisplayName("agrees with Collections.binarySearch on random data")
        void agreesWithLibrarySearch() {
            Random random = new Random(7);
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                values.add(random.nextInt(1000));
            }
            Collections.sort(values);

            for (int probe = 0; probe < 1000; probe++) {
                int key = random.nextInt(1000);
                int mine = Sorting.binarySearch(values, key, Comparator.<Integer>naturalOrder());
                int theirs = Collections.binarySearch(values, key);

                // With duplicates the two may land on different indices, so
                // the contract is agreement on presence and on insertion point.
                if (theirs >= 0) {
                    assertTrue(mine >= 0, "key " + key + " present but not found");
                    assertEquals(values.get(theirs), values.get(mine));
                } else {
                    assertEquals(theirs, mine, "insertion point differs for " + key);
                }
            }
        }

        @Test
        @DisplayName("handles an empty list")
        void handlesEmptyList() {
            assertEquals(-1, Sorting.binarySearch(List.<Integer>of(), 5,
                    Comparator.<Integer>naturalOrder()));
        }

        @Test
        @DisplayName("lowerBound finds the first of a run of duplicates")
        void lowerBoundFindsFirstDuplicate() {
            // binarySearch may return any match; a range query needs the first.
            List<Integer> withDuplicates = List.of(1, 2, 2, 2, 2, 3, 4);

            assertEquals(1, Sorting.lowerBound(withDuplicates, 2, Comparator.<Integer>naturalOrder()));
            assertEquals(5, Sorting.lowerBound(withDuplicates, 3, Comparator.<Integer>naturalOrder()));
        }

        @Test
        @DisplayName("linear search agrees with binary search")
        void linearSearchAgrees() {
            for (Integer value : sorted) {
                assertTrue(Sorting.linearSearch(sorted, value, Comparator.<Integer>naturalOrder()) >= 0);
            }
            assertEquals(-1, Sorting.linearSearch(sorted, 4, Comparator.<Integer>naturalOrder()));
        }

        @Test
        @DisplayName("isSorted detects unordered input")
        void isSortedDetectsDisorder() {
            assertTrue(Sorting.isSorted(sorted, Comparator.<Integer>naturalOrder()));
            assertFalse(Sorting.isSorted(List.of(1, 3, 2), Comparator.<Integer>naturalOrder()));
        }
    }

    @Nested
    @DisplayName("Inverted index")
    class InvertedIndexTests {

        private InvertedIndex indexed() {
            InvertedIndex index = new InvertedIndex();
            index.add(1, "Designing a Blogging Schema", "normalization and third normal form");
            index.add(2, "Choosing an Index for Text Search", "trigram and full text indexes");
            index.add(3, "Caching Without Lying", "LRU cache invalidation and consistency");
            return index;
        }

        @Test
        @DisplayName("finds documents by a single term")
        void findsSingleTerm() {
            assertEquals(Set.of(1L), indexed().lookup("normalization"));
            assertEquals(Set.of(3L), indexed().lookup("lru"));
        }

        @Test
        @DisplayName("lookup ignores case")
        void lookupIgnoresCase() {
            InvertedIndex index = indexed();

            assertEquals(index.lookup("caching"), index.lookup("CACHING"));
            assertFalse(index.lookup("Schema").isEmpty());
        }

        @Test
        @DisplayName("a multi-term search returns only documents containing every term")
        void multiTermSearchIsAnAnd() {
            InvertedIndex index = indexed();

            assertEquals(Set.of(2L), index.search("text search"));
            assertTrue(index.search("normalization trigram").isEmpty(),
                    "no document contains both terms");
        }

        @Test
        @DisplayName("an OR search returns documents containing any term")
        void searchAnyIsAnOr() {
            assertEquals(Set.of(1L, 3L), indexed().searchAny("normalization lru"));
        }

        @Test
        @DisplayName("stop words are not indexed")
        void stopWordsExcluded() {
            // "and" appears in every document; indexing it costs memory and
            // returns everything, which is the opposite of selective.
            assertTrue(indexed().lookup("and").isEmpty());
            assertTrue(indexed().lookup("the").isEmpty());
        }

        @Test
        @DisplayName("single characters are not indexed")
        void singleCharactersExcluded() {
            InvertedIndex index = new InvertedIndex();
            index.add(1, "B-tree versus GIN");

            assertTrue(index.lookup("b").isEmpty());
            assertEquals(Set.of(1L), index.lookup("tree"));
        }

        @Test
        @DisplayName("re-adding a document replaces its old terms")
        void reAddingReplacesTerms() {
            // Without this an edited title keeps matching words it no longer
            // contains, which is a silently wrong search result.
            InvertedIndex index = new InvertedIndex();
            index.add(1, "original title about caching");
            index.add(1, "replacement title about indexing");

            assertTrue(index.lookup("caching").isEmpty(), "the old term should be gone");
            assertEquals(Set.of(1L), index.lookup("indexing"));
            assertEquals(1, index.documentCount());
        }

        @Test
        @DisplayName("removing a document clears its postings and empty terms")
        void removeClearsPostings() {
            InvertedIndex index = new InvertedIndex();
            index.add(1, "unique vocabulary here");
            int termsBefore = index.termCount();

            index.remove(1);

            assertEquals(0, index.documentCount());
            assertEquals(0, index.termCount(),
                    "empty posting lists leak terms; expected all " + termsBefore + " removed");
        }

        @Test
        @DisplayName("an empty or unknown query returns nothing")
        void emptyQueryReturnsNothing() {
            InvertedIndex index = indexed();

            assertTrue(index.search("").isEmpty());
            assertTrue(index.search(null).isEmpty());
            assertTrue(index.search("zygomorphic").isEmpty());
            assertTrue(index.lookup(null).isEmpty());
        }

        @Test
        @DisplayName("tokenize lower-cases, splits and filters")
        void tokenizeBehaviour() {
            assertEquals(List.of("hello", "world"),
                    InvertedIndex.tokenize("Hello, WORLD!"));
            assertEquals(List.of("post", "2026", "update"),
                    InvertedIndex.tokenize("post-2026_update"));
            assertEquals(List.of(), InvertedIndex.tokenize("   "));
            assertEquals(List.of(), InvertedIndex.tokenize(null));
        }

        @Test
        @DisplayName("the index agrees with a linear scan over many documents")
        void agreesWithLinearScan() {
            // The index is only worth having if it returns what scanning
            // would. This is that guarantee, over a corpus big enough to be
            // a real test rather than a demonstration.
            Random random = new Random(11);
            String[] vocabulary = {"alpha", "bravo", "charlie", "delta", "echo",
                                   "foxtrot", "golf", "hotel", "india", "juliet"};

            List<String> documents = new ArrayList<>();
            InvertedIndex index = new InvertedIndex();

            for (int id = 0; id < 400; id++) {
                StringBuilder text = new StringBuilder();
                for (int w = 0; w < 8; w++) {
                    text.append(vocabulary[random.nextInt(vocabulary.length)]).append(' ');
                }
                documents.add(text.toString());
                index.add(id, text.toString());
            }

            for (String term : vocabulary) {
                Set<Long> fromIndex = index.lookup(term);

                Set<Long> fromScan = new java.util.HashSet<>();
                for (int id = 0; id < documents.size(); id++) {
                    if (InvertedIndex.tokenize(documents.get(id)).contains(term)) {
                        fromScan.add((long) id);
                    }
                }
                assertEquals(fromScan, fromIndex, "index and scan disagree on \"" + term + "\"");
            }
        }
    }
}
