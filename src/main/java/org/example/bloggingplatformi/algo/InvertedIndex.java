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
 * An in-memory inverted index: term to the set of post ids containing it.
 *
 * <p>This is the hashing structure Epic 3.1 asks for, and it is the same idea
 * the database's GIN index implements on disk. Building it in memory makes the
 * mechanism visible and gives Phase 4 something to measure the database
 * against.
 *
 * <p><b>Why it is fast.</b> A naive keyword search scans every post and tests
 * every title, which is O(n·m) for n posts of m words. The inverted index pays
 * that cost once at build time and then answers a single-term query in O(1):
 * one hash probe returns the matching ids directly. A multi-term query
 * intersects the posting lists, starting from the smallest, so the work is
 * bounded by the rarest term rather than by the corpus.
 *
 * <p><b>What it is not.</b> There is no stemming, so "index" does not match
 * "indexing", and no ranking — every hit is equal. PostgreSQL's
 * {@code to_tsvector} does both. That difference is a finding for the
 * comparison, not something to paper over.
 */
public class InvertedIndex {

    /**
     * Words carrying no discriminating power. Indexing them costs memory and
     * returns almost every document, which is the opposite of selective.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for",
            "from", "has", "have", "in", "is", "it", "its", "of", "on", "or",
            "that", "the", "this", "to", "was", "were", "will", "with");

    /** Single characters match too much to be worth a posting list. */
    private static final int MIN_TERM_LENGTH = 2;

    private final Map<String, Set<Long>> postings = new HashMap<>();
    private final Map<Long, Set<String>> termsByDocument = new HashMap<>();

    /** Indexes one document under every distinct term in the given texts. */
    public void add(long documentId, String... texts) {
        Set<String> terms = new LinkedHashSet<>();
        for (String text : texts) {
            terms.addAll(tokenize(text));
        }

        // Replacing an existing document, so its old terms are removed first;
        // otherwise an edited title keeps matching words it no longer contains.
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
                // An empty posting list is a leak: the term would otherwise
                // stay in the map forever, inflating termCount and memory.
                if (ids.isEmpty()) {
                    postings.remove(term);
                }
            }
        }
    }

    /** Ids containing one exact term. O(1). */
    public Set<Long> lookup(String term) {
        if (term == null || term.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = postings.get(normalize(term));
        return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
    }

    /**
     * Ids containing <em>every</em> term in the query (an AND search).
     *
     * <p>The posting lists are intersected smallest-first. Intersection cost is
     * bounded by the smallest list, so starting there means a query like
     * "the postgresql" does work proportional to the few "postgresql"
     * documents rather than to the many matching the common word.
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
                // One term matches nothing, so the AND result is empty and
                // there is no point intersecting anything.
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

    /** Ids containing at least one term (an OR search). */
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

    /** Distinct indexed terms: the index's width. */
    public int termCount() {
        return postings.size();
    }

    public int documentCount() {
        return termsByDocument.size();
    }

    /** Total postings, which is what the index actually costs in memory. */
    public int postingCount() {
        return postings.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Splits text into normalized terms.
     *
     * <p>Splitting on everything that is not a letter or digit means "B-tree"
     * yields the two terms "tree" and nothing else, since "b" falls below the
     * minimum length. A search for "b-tree" tokenizes the same way and so
     * still finds the post. Accents are left alone here; the database handles
     * those through its own collation.
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
