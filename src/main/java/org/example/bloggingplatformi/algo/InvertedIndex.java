package org.example.bloggingplatformi.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory inverted index: a hash map from each word to the ids of the
 * posts that contain it. It is the same idea as a GIN index in PostgreSQL.
 *
 * How it works: we split each post into words once, when it is added. A
 * one-word search is then a single hash lookup, O(1), instead of scanning
 * every post, O(n). A multi-word search intersects the id sets, smallest first.
 *
 * Limits: no stemming ("index" does not match "indexing") and no ranking.
 */
public class InvertedIndex {

    /** Common words we skip, because almost every post contains them. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for",
            "from", "has", "have", "in", "is", "it", "its", "of", "on", "or",
            "that", "the", "this", "to", "was", "were", "will", "with");

    /** Single letters match too many posts to be useful. */
    private static final int MIN_TERM_LENGTH = 2;

    private final Map<String, Set<Long>> postings = new HashMap<>();
    private final Map<Long, Set<String>> termsByDocument = new HashMap<>();

    /** Indexes one document under every distinct term in the given texts. */
    public void add(long documentId, String... texts) {
        Set<String> terms = new LinkedHashSet<>();
        for (String text : texts) {
            terms.addAll(tokenize(text));
        }

        // Remove the old words first, so an edited post does not match words it no longer has.
        remove(documentId);

        for (String term : terms) {
            postings.computeIfAbsent(term, key -> new HashSet<>()).add(documentId);
        }
        termsByDocument.put(documentId, terms);
    }

    /** Removes a document from every posting list it appears in. */
    public void remove(long documentId) {
        Set<String> terms = termsByDocument.remove(documentId);
        if (terms == null) {
            return;
        }
        for (String term : terms) {
            Set<Long> ids = postings.get(term);
            if (ids != null) {
                ids.remove(documentId);
                // Drop the word when no post uses it, so the map does not keep growing.
                if (ids.isEmpty()) {
                    postings.remove(term);
                }
            }
        }
    }

    /** Ids of posts containing one exact word. O(1). */
    public Set<Long> lookup(String term) {
        if (term == null || term.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = postings.get(normalize(term));
        return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
    }

    /**
     * Ids of posts containing every word in the query (an AND search).
     * We start from the smallest id set, so the work stays small.
     */
    public Set<Long> search(String query) {
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return Set.of();
        }

        List<Set<Long>> lists = new ArrayList<>(terms.size());
        for (String term : terms) {
            Set<Long> ids = postings.get(term);
            if (ids == null || ids.isEmpty()) {
                // One word has no matches, so the AND result must be empty.
                return Set.of();
            }
            lists.add(ids);
        }
        lists.sort((left, right) -> Integer.compare(left.size(), right.size()));

        Set<Long> result = new HashSet<>(lists.get(0));
        for (int i = 1; i < lists.size() && !result.isEmpty(); i++) {
            result.retainAll(lists.get(i));
        }
        return result;
    }

    /** Ids of posts containing at least one word (an OR search). */
    public Set<Long> searchAny(String query) {
        Set<Long> result = new HashSet<>();
        for (String term : tokenize(query)) {
            Set<Long> ids = postings.get(term);
            if (ids != null) {
                result.addAll(ids);
            }
        }
        return result;
    }

    public void clear() {
        postings.clear();
        termsByDocument.clear();
    }

    /** Number of different words in the index. */
    public int termCount() {
        return postings.size();
    }

    public int documentCount() {
        return termsByDocument.size();
    }

    /** Total (word, post id) pairs stored. This is the main memory cost. */
    public int postingCount() {
        return postings.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Splits text into lowercase words on anything that is not a letter or digit.
     * Stop words and one-letter words are dropped, so "B-tree" gives just "tree".
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() >= MIN_TERM_LENGTH && !STOP_WORDS.contains(raw)) {
                terms.add(raw);
            }
        }
        return terms;
    }

    private static String normalize(String term) {
        return term.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "InvertedIndex[documents=" + documentCount()
                + ", terms=" + termCount()
                + ", postings=" + postingCount() + "]";
    }
}
